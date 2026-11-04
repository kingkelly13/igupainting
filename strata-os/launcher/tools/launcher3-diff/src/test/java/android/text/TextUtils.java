package android.text;

/** JVM stand-in for the one TextUtils method StringMatcherUtility uses. */
public final class TextUtils {
    private TextUtils() {}

    public static int indexOf(CharSequence s, char ch) {
        return s.toString().indexOf(ch);
    }
}
