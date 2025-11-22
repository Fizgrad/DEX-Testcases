import java.util.Locale;

public class LocalePrintfRepro {
  public static void main(String[] args) {
    System.out.printf(Locale.ROOT, "Hello, World! from LocalePrintfRepro\n");
    System.out.printf("Hello, World! from %d\n", 12345);
    System.out.printf(Locale.ROOT, "Hello, World! from %d\n", 12345);
  }
}
