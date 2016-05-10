package com.skedgo.generator;



/**
 * Tim's class for helping to parse mini-syntaxes such as the car-park rules
 * or persistent versions of Trip's.
 */
public class Parser {
    private String s;
    private int n;

    public Parser(String _s)
    {
        s = _s;
        n = 0;
    }

    public Parser(Parser other)
    {
        s = other.s;
        n = other.n;
    }

    public char peek()
    {
        if (n >= s.length())
            return '\0';
        return s.charAt(n);
    }

    public char getChar()
    {
        if (n >= s.length())
            return '\0';
        return s.charAt(n++);
    }

    /** Peeks the next char, without gobbling it. Tells us if it's a digit or not. */
    public boolean looksLikeNumberComing()
    {
        if (n >= s.length())
            return false;
        return Character.isDigit(s.charAt(n));
    }

    public void skip(char ch)
    {
        skipWhitespace();
        if (Character.toUpperCase(peek()) == Character.toUpperCase(ch))
            n++;
    }

    public void skip(String expected)
    {
        for (int i=0; i < expected.length(); i++)
            skip(expected.charAt(i));
    }

    public void skip()
    {
        n++;
    }

    public void skipToEnd()
    {
        n = s.length();
    }

    public void skipTo(char ch)
    {
        while (n < s.length() && s.charAt(n) != ch)
            n++;
        if (n < s.length())
            n++;
    }

    public boolean findAndSwallow(String expected)
    {
        while (! finished()) {
            if (matches(expected))
                return true;
            skip();
        }
        return false;
    }

    public void skipRemainderOfToken()
    {
        while (Character.isLetter(peek()))
            n++;
    }

    /** If the input matches this string (case insensitive) then advance to the end of this prefix
     * and return 'true'. */
    public boolean matches(String expected)
    {
        skipWhitespace();
        for (int i=0; i < expected.length(); i++) {
            if (i + n >= s.length())
                return false;
            if (Character.toUpperCase(s.charAt(i+n)) != Character.toUpperCase(expected.charAt(i)))
                return false;
        }
        n += expected.length();
        return true;
    }

    public void skipWhitespace()
    {
        do {
            char ch = peek();
            if (Character.isWhitespace(ch))
                n++;
            else if (ch == '\\' && n + 1 < s.length() && s.charAt(n+1) == '\n')
                n += 2;
            else break;
        } while (true);
    }

    public int getInt()
    {
        int i=0;
        skipWhitespace();
        if (n >= s.length())
            return 0;
        boolean negative = false;
        if (peek() == '-') {
            negative = true;
            n++;
        }
        while (n < s.length() && Character.isDigit(peek()))
            i = i*10 + s.charAt(n++) - '0';
        if (negative)
            i = -i;
        return i;
    }

    public long getLong()
    {
        long i=0;
        skipWhitespace();
        if (n >= s.length())
            return 0;
        boolean negative = false;
        if (peek() == '-') {
            negative = true;
            n++;
        }
        while (n < s.length() && Character.isDigit(peek()))
            i = i*10 + s.charAt(n++) - '0';
        if (negative)
            i = -i;
        return i;
    }

    /** This fn returns the string up to but not including the terminator.
     * It also gobbles the terminator. */
    public String getToken(char terminator)
    {
        skipWhitespace();
        StringBuilder o = new StringBuilder();
        while (n < s.length()) {
            char ch = s.charAt(n++);
            if (ch == terminator)
                break;
            o.append(ch);
        }
        return o.toString();
    }

    /** underscores ('_') are included in the token. */
    public String getAlnumToken()
    {
        skipWhitespace();
        StringBuilder o = new StringBuilder();
        while (n < s.length()) {
            char ch = s.charAt(n++);
            if (! Character.isLetterOrDigit(ch) && ch != '_') {
                n--;
                break;
            }
            o.append(ch);
        }
        return o.toString();
    }

    public double getDouble()
    {
        StringBuilder o = new StringBuilder();
        skipWhitespace();
        while (n < s.length()) {
            char ch = s.charAt(n++);
            if (! Character.isDigit(ch) && ch != '.' && ch != '-') {
                n--;
                break;
            }
            o.append(ch);
        }
        try {
            return Double.parseDouble(o.toString());
        }
        catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public String getQuotedString()
    {
        if (peek() == '"') {
            StringBuilder o = new StringBuilder();
            n++;
            while (n < s.length()) {
                char ch = getChar();
                if (ch == '"')
                    break;
                if (ch == '\\')
                    o.append(getChar());
                else o.append(ch);
            }
            return o.toString();
        }
        else return getToken(' ');
    }

    public boolean finished()
    {
        return n >= s.length();
    }

    public String toString()
    {
        String prefix;
        if (n > 10)
            prefix = s.substring(n-10, n);
        else prefix = s.substring(0,n);
        return prefix + "^" + s.substring(n);
    }

    public String getRemainder()
    {
        return s.substring(n);
    }

    public int mark()
    {
        return n;
    }

    public void restore(int mark)
    {
        n = mark;
    }
}
