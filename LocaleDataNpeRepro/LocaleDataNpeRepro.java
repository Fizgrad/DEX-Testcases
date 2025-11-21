import java.util.Locale;

public class LocaleDataNpeRepro {
  public static void main(String[] args) {
    System.out.printf(Locale.ROOT, "Hello, World! from LocaleDataNpeRepro\n");
    System.out.printf("Hello, World! from %d\n", 12345);
    System.out.printf(Locale.ROOT, "Hello, World! from %d\n", 12345);
  }
}
