package com.art.tests.icu;

import android.icu.lang.UCharacter;
import android.icu.text.BreakIterator;
import android.icu.text.Collator;
import android.icu.text.CompactDecimalFormat;
import android.icu.text.DateFormat;
import android.icu.text.DecimalFormat;
import android.icu.text.MeasureFormat;
import android.icu.text.MessageFormat;
import android.icu.text.Normalizer2;
import android.icu.text.NumberFormat;
import android.icu.text.PluralRules;
import android.icu.text.RelativeDateTimeFormatter;
import android.icu.text.SimpleDateFormat;
import android.icu.text.Transliterator;
import android.icu.text.UnicodeSet;
import android.icu.util.Calendar;
import android.icu.util.Currency;
import android.icu.util.GregorianCalendar;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.icu.util.TimeZone;
import android.icu.util.ULocale;
import android.icu.util.VersionInfo;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ICUTestSuite {
  private static final List<TestCase> TESTS = Arrays.asList(
      new TestCase("ULocale coverage", ICUTestSuite::testLocaleBasics),
      new TestCase("Number formatting", ICUTestSuite::testNumberFormatting),
      new TestCase("Currency formatting", ICUTestSuite::testCurrencyFormatting),
      new TestCase("Compact decimals", ICUTestSuite::testCompactDecimalFormat),
      new TestCase("Calendar + DateFormat", ICUTestSuite::testDateAndCalendar),
      new TestCase("Time zone math", ICUTestSuite::testTimeZoneRoundTrip),
      new TestCase("Collation", ICUTestSuite::testCollation),
      new TestCase("BreakIterator", ICUTestSuite::testBreakIterator),
      new TestCase("Transliteration", ICUTestSuite::testTransliteration),
      new TestCase("UnicodeSet + character data", ICUTestSuite::testUnicodeSetOperations),
      new TestCase("Normalization + casing", ICUTestSuite::testNormalizationAndCase),
      new TestCase("MessageFormat + PluralRules", ICUTestSuite::testMessageAndPluralRules),
      new TestCase("MeasureFormat", ICUTestSuite::testMeasureFormat),
      new TestCase("RelativeDateTimeFormatter", ICUTestSuite::testRelativeDateTimeFormatter),
      new TestCase("VersionInfo", ICUTestSuite::testVersionInfo),
      new TestCase("Calendar add/roll", ICUTestSuite::testCalendarArithmetic));

  public static void main(String[] args) {
    int passed = 0;
    long suiteStart = System.nanoTime();
    for (TestCase test : TESTS) {
      System.out.println("Running ICU test: " + test.name);
      long start = System.nanoTime();
      try {
        test.body.run();
        long durMs = (System.nanoTime() - start) / 1_000_000L;
        System.out.println("PASS (" + durMs + " ms): " + test.name);
        passed++;
      } catch (Throwable t) {
        System.err.println("FAIL: " + test.name);
        t.printStackTrace();
        System.exit(1);
      }
    }
    long totalMs = (System.nanoTime() - suiteStart) / 1_000_000L;
    System.out.println("SUCCESS: " + passed + " ICU test cases passed in " + totalMs + " ms");
  }

  private static void testLocaleBasics() {
    ULocale zhHans = ULocale.forLanguageTag("zh-Hans-CN");
    log("Locale", "Parsed zh-Hans locale -> " + zhHans);
    assertEquals("zh_Hans_CN", zhHans.toString(), "Likely subtags retain script/region");

    ULocale likely = ULocale.addLikelySubtags(new ULocale("sr"));
    log("Locale", "Likely subtags for sr -> " + likely);
    assertTrue(likely.getScript().length() > 0, "Likely subtags should add a script");

    ULocale minimized = ULocale.minimizeSubtags(likely);
    assertEquals("sr", minimized.getLanguage(), "Minimize retains language");
    assertTrue(ULocale.getAvailableLocales().length > 50, "ICU should expose many locales");
  }

  private static void testNumberFormatting() {
    NumberFormat nf = NumberFormat.getInstance(ULocale.GERMANY);
    log("NumberFormat", "Using locale " + ULocale.GERMANY);
    String formatted = nf.format(12345.678);
    log("NumberFormat", "Formatted 12345.678 -> " + formatted);
    Number parsed;
    try {
      parsed = nf.parse(formatted);
    } catch (ParseException e) {
      throw new AssertionError("Number parse failed", e);
    }
    assertApproxEquals(12345.678, parsed.doubleValue(), 0.01, "Locale parsing round-trip");

    DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(ULocale.JAPAN);
    df.applyPattern("#,##0.00");
    String custom = df.format(7654321.5);
    log("NumberFormat", "Custom pattern output -> " + custom);
    assertTrue(custom.contains(","), "Custom pattern should group digits");
  }

  private static void testCurrencyFormatting() {
    NumberFormat currency = NumberFormat.getCurrencyInstance(ULocale.CANADA_FRENCH);
    currency.setCurrency(Currency.getInstance("EUR"));
    String formatted = currency.format(42.5);
    log("Currency", "EUR formatted in fr-CA -> " + formatted);
    assertTrue(formatted.contains("€") || formatted.contains("EUR"),
        "Currency output should include symbol");
    Number parsed;
    try {
      parsed = currency.parse(formatted);
    } catch (ParseException e) {
      throw new AssertionError("Currency parse failed", e);
    }
    assertApproxEquals(42.5, parsed.doubleValue(), 0.01, "Currency parsing should round-trip");
  }

  private static void testCompactDecimalFormat() {
    CompactDecimalFormat shortFmt =
        CompactDecimalFormat.getInstance(ULocale.US, CompactDecimalFormat.CompactStyle.SHORT);
    String twelveHundred = shortFmt.format(1_200);
    log("CompactDecimal", "US short style 1200 -> " + twelveHundred);
    assertTrue(twelveHundred.contains("K") || twelveHundred.contains("1.2"),
        "Compact decimals should abbreviate thousands");

    CompactDecimalFormat longFmt =
        CompactDecimalFormat.getInstance(ULocale.CHINA, CompactDecimalFormat.CompactStyle.LONG);
    String millions = longFmt.format(1_500_000);
    log("CompactDecimal", "CN long style 1.5M -> " + millions);
    assertTrue(millions.length() < 10, "Long style should be localized");
  }

  private static void testDateAndCalendar() {
    Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("Europe/Paris"), ULocale.FRANCE);
    cal.clear();
    cal.set(2021, Calendar.JULY, 14, 9, 30, 0);
    cal.add(Calendar.DAY_OF_MONTH, 20);
    assertEquals(2021, cal.get(Calendar.YEAR), "Year add should roll over correctly");
    assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH), "Month should advance to August");

    DateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm z", ULocale.US);
    iso.setTimeZone(TimeZone.getTimeZone("UTC"));
    String formatted = iso.format(cal.getTime());
    log("Calendar", "UTC formatted date -> " + formatted);
    assertTrue(formatted.startsWith("2021-08"), "ISO formatting should show August");
  }

  private static void testTimeZoneRoundTrip() {
    TimeZone shanghai = TimeZone.getTimeZone("Asia/Shanghai");
    Calendar local = new GregorianCalendar(shanghai, ULocale.CHINA);
    local.set(2022, Calendar.JANUARY, 1, 8, 0, 0);
    local.set(Calendar.MILLISECOND, 0);

    Calendar utc = new GregorianCalendar(TimeZone.getTimeZone("UTC"), ULocale.US);
    utc.setTimeInMillis(local.getTimeInMillis());

    int offsetHours = shanghai.getOffset(local.getTimeInMillis()) / (60 * 60 * 1000);
    int computed = (local.get(Calendar.HOUR_OF_DAY) - utc.get(Calendar.HOUR_OF_DAY) + 24) % 24;
    log("TimeZone", "Shanghai offset hours=" + offsetHours + ", computed delta=" + computed);
    assertEquals(offsetHours, computed, "Time zone offset should match calendar delta");
  }

  private static void testCollation() {
    Collator collator = Collator.getInstance(new ULocale("sv_SE"));
    collator.setStrength(Collator.SECONDARY);
    int result = collator.compare("ångström", "zebra");
    log("Collation", "Compare ångström vs zebra -> " + result);
    assertTrue(result > 0, "Swedish Å should sort after Z");
  }

  private static void testBreakIterator() {
    String text = "Hello ICU 世界!";
    BreakIterator iterator = BreakIterator.getWordInstance(ULocale.US);
    iterator.setText(text);
    List<String> words = new ArrayList<>();
    int start = iterator.first();
    for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
      String candidate = text.substring(start, end).trim();
      if (!candidate.isEmpty()) {
        words.add(candidate);
      }
    }
    assertTrue(words.contains("Hello"), "Word iterator should see English tokens");
    assertTrue(words.contains("世界"), "Word iterator should see CJK tokens");
    log("BreakIterator", "Detected tokens -> " + words);
  }

  private static void testTransliteration() {
    Transliterator transliterator = Transliterator.getInstance("Any-Latin; Latin-ASCII");
    String greek = "Ελλάδα";
    String transliterated = transliterator.transliterate(greek);
    log("Transliterator", "Ελλάδα -> " + transliterated);
    assertTrue(transliterated.toLowerCase(Locale.ROOT).contains("ellada"),
        "Greek should transliterate to Latin");
  }

  private static void testUnicodeSetOperations() {
    UnicodeSet latinAndHan = new UnicodeSet("[a-zA-Z{汉}{字}]");
    latinAndHan.add("汉字"); // add a multi-code-point sequence explicitly
    latinAndHan.addAll(new UnicodeSet("[:Nd:]"));
    assertTrue(latinAndHan.contains('A'), "UnicodeSet should contain uppercase letters");
    assertTrue(latinAndHan.contains('9'), "UnicodeSet should include digits");
    assertTrue(latinAndHan.contains("汉字"), "UnicodeSet should match multi-code-point strings");
    assertTrue(!latinAndHan.contains('@'), "UnicodeSet should exclude symbols");
    log("UnicodeSet", "Set size after unions -> " + latinAndHan.size());
  }

  private static void testNormalizationAndCase() {
    Normalizer2 nfc = Normalizer2.getNFCInstance();
    String decomposed = "e\u0301";
    String normalized = nfc.normalize(decomposed);
    log("Normalizer", "NFC(e + accent) -> " + normalized);
    assertEquals("é", normalized, "NFC should compose combining marks");
    assertTrue(nfc.isNormalized(normalized), "Composed string should report normalized");

    String upper = UCharacter.toUpperCase(ULocale.GERMANY, "straße");
    log("Normalizer", "Upper STRASSE -> " + upper);
    assertEquals("STRASSE", upper, "German sharp s should expand when upper-cased");
  }

  private static void testMessageAndPluralRules() {
    String pattern = "{0, plural, one{# file was} other{# files were}} "
        + "{1, select, female{uploaded by her.} male{uploaded by him.} other{uploaded by them.}}";
    MessageFormat fmt = new MessageFormat(pattern, ULocale.US);
    String singular = fmt.format(new Object[] {1, "female"});
    assertTrue(singular.contains("1 file"), "Plural pattern should handle one");
    assertTrue(singular.contains("her"), "Select pattern should pick female branch");
    String plural = fmt.format(new Object[] {5, "other"});
    assertTrue(plural.contains("5 files"), "Plural pattern should handle other");

    PluralRules rules = PluralRules.forLocale(ULocale.ENGLISH);
    assertEquals("one", rules.select(1), "English plural rule for 1 is 'one'");
    assertEquals("other", rules.select(2), "English plural rule for 2 is 'other'");
  }

  private static void testMeasureFormat() {
    MeasureFormat format =
        MeasureFormat.getInstance(ULocale.US, MeasureFormat.FormatWidth.SHORT);
    String duration = format.formatMeasures(new Measure(90, MeasureUnit.MINUTE),
        new Measure(15, MeasureUnit.SECOND));
    assertTrue(duration.contains("min"), "Short format should contain minutes abbreviation");
    log("MeasureFormat", "Duration string -> " + duration);
  }

  private static void testRelativeDateTimeFormatter() {
    RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance(ULocale.US);
    String inTwoHours =
        formatter.format(2, RelativeDateTimeFormatter.Direction.NEXT,
            RelativeDateTimeFormatter.RelativeUnit.HOURS);
    assertTrue(inTwoHours.toLowerCase(Locale.US).contains("2"),
        "Relative formatter should include quantity");
    String yesterday =
        formatter.format(RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.AbsoluteUnit.DAY);
    assertTrue(yesterday.toLowerCase(Locale.US).contains("yesterday"),
        "Absolute unit formatting should return localized keyword");
    log("RelativeDate", "Examples: " + inTwoHours + " / " + yesterday);
  }

  private static void testVersionInfo() {
    VersionInfo icuVersion = VersionInfo.ICU_VERSION;
    assertTrue(icuVersion.getMajor() >= 50, "ICU major version should be modern");
    VersionInfo unicodeVersion = UCharacter.getUnicodeVersion();
    assertTrue(unicodeVersion.getMajor() >= 5, "Unicode version should reflect current data");
  }

  private static void testCalendarArithmetic() {
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), ULocale.US);
    calendar.clear();
    calendar.set(2020, Calendar.DECEMBER, 31);
    calendar.add(Calendar.DAY_OF_YEAR, 1);
    assertEquals(2021, calendar.get(Calendar.YEAR), "Day-of-year add should cross year");
    assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH), "Month should wrap to January");
    calendar.roll(Calendar.MONTH, 1);
    assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH), "Roll should advance month");
  }

  private static void assertEquals(Object expected, Object actual, String message) {
    if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
      throw new AssertionError(message + " Expected=" + expected + " Actual=" + actual);
    }
  }

  private static void assertTrue(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void assertApproxEquals(double expected, double actual, double delta,
      String message) {
    if (Math.abs(expected - actual) > delta) {
      throw new AssertionError(message + " Expected=" + expected + " Actual=" + actual);
    }
  }

  private static void log(String test, String message) {
    System.out.println("[" + test + "] " + message);
  }

  private static final class TestCase {
    final String name;
    final CheckedRunnable body;

    TestCase(String name, CheckedRunnable body) {
      this.name = name;
      this.body = body;
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
