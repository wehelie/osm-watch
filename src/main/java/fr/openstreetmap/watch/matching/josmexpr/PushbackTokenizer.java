package fr.openstreetmap.watch.matching.josmexpr;
// License: GPL. Copyright 2007 by Immanuel Scholz and others

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import fr.openstreetmap.watch.matching.josmexpr.SearchCompiler;
import fr.openstreetmap.watch.matching.josmexpr.SearchCompiler.ParseError;


public class PushbackTokenizer {

    public static class Range {
        private final long start;
        private final long end;

        public Range(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }
    }

    private final Reader search;

    private Token currentToken;
    private String currentText;
    private Long currentNumber;
    private Long currentRange;
    private int c;
    private boolean isRange;

    public PushbackTokenizer(Reader search) {
        this.search = search;
        getChar();
    }

    public enum Token {
        NOT(("<not>")), OR(("<or>")), XOR(("<xor>")), LEFT_PARENT(("<left parent>")),
        RIGHT_PARENT(("<right parent>")), COLON(("<colon>")), EQUALS(("<equals>")),
        KEY(("<key>")), QUESTION_MARK(("<question mark>")),
        EOF(("<end-of-file>"));

        private Token(String name) {
            this.name = name;
        }

        private final String name;

        @Override
        public String toString() {
            return name;
        }
    }


    private void getChar() {
        try {
            c = search.read();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static final List<Character> specialChars = Arrays.asList(new Character[] {'"', ':', '(', ')', '|', '^', '=', '?'});
    private static final List<Character> specialCharsQuoted = Arrays.asList(new Character[] {'"'});

    private String getString(boolean quoted) {
        List<Character> sChars = quoted ? specialCharsQuoted : specialChars;
        StringBuilder s = new StringBuilder();
        boolean escape = false;
        while (c != -1 && (escape || (!sChars.contains((char)c) && (quoted || !Character.isWhitespace(c))))) {
            if (c == '\\' && !escape) {
                escape = true;
            } else {
                s.append((char)c);
                escape = false;
            }
            getChar();
        }
        return s.toString();
    }

    private String getString() {
        return getString(false);
    }

    /**
     * The token returned is <code>null</code> or starts with an identifier character:
     * - for an '-'. This will be the only character
     * : for an key. The value is the next token
     * | for "OR"
     * ^ for "XOR"
     * ' ' for anything else.
     * @return The next token in the stream.
     */
    public Token nextToken() {
        if (currentToken != null) {
            Token result = currentToken;
            currentToken = null;
            return result;
        }

        while (Character.isWhitespace(c)) {
            getChar();
        }
        switch (c) {
        case -1:
            getChar();
            return Token.EOF;
        case ':':
            getChar();
            return Token.COLON;
        case '=':
            getChar();
            return Token.EQUALS;
        case '(':
            getChar();
            return Token.LEFT_PARENT;
        case ')':
            getChar();
            return Token.RIGHT_PARENT;
        case '|':
            getChar();
            return Token.OR;
        case '^':
            getChar();
            return Token.XOR;
        case '&':
            getChar();
            return nextToken();
        case '?':
            getChar();
            return Token.QUESTION_MARK;
        case '"':
            getChar();
            currentText = getString(true);
            getChar();
            return Token.KEY;
        default:
            String prefix = "";
            if (c == '-') {
                getChar();
                if (!Character.isDigit(c))
                    return Token.NOT;
                prefix = "-";
            }
            currentText = prefix + getString();
            if ("or".equalsIgnoreCase(currentText))
                return Token.OR;
            else if ("xor".equalsIgnoreCase(currentText))
                return Token.XOR;
            else if ("and".equalsIgnoreCase(currentText))
                return nextToken();
            // try parsing number
            try {
                currentNumber = Long.parseLong(currentText);
            } catch (NumberFormatException e) {
                currentNumber = null;
            }
            // if text contains "-", try parsing a range
            int pos = currentText.indexOf('-', 1);
            isRange = pos > 0;
            if (isRange) {
                try {
                    currentNumber = Long.parseLong(currentText.substring(0, pos));
                } catch (NumberFormatException e) {
                    currentNumber = null;
                }
                try {
                    currentRange = Long.parseLong(currentText.substring(pos + 1));
                } catch (NumberFormatException e) {
                    currentRange = null;
                }
            } else {
                currentRange = null;
            }
            return Token.KEY;
        }
    }

    public boolean readIfEqual(Token token) {
        Token nextTok = nextToken();
        if (token.equals(nextTok)) 
            return true;
        currentToken = nextTok;
        return false;
    }

    public String readTextOrNumber() {
        Token nextTok = nextToken();
        if (nextTok == Token.KEY)
            return currentText;
        currentToken = nextTok;
        return null;
    }

    public long readNumber(String errorMessage) throws ParseError {
        if ((nextToken() == Token.KEY) && (currentNumber != null))
            return currentNumber;
        else
            throw new ParseError(errorMessage);
    }

    public long getReadNumber() {
        return (currentNumber != null) ? currentNumber : 0;
    }

    public Range readRange(String errorMessage) throws ParseError {
        if (nextToken() != Token.KEY || (currentNumber == null && currentRange == null)) {
            throw new ParseError(errorMessage);
        } else if (!isRange && currentNumber != null) {
            if (currentNumber >= 0) {
                return new Range(currentNumber, currentNumber);
            } else {
                return new Range(0, Math.abs(currentNumber));
            }
        } else if (isRange && currentRange == null) {
            return new Range(currentNumber, Integer.MAX_VALUE);
        } else if (currentNumber != null && currentRange != null) {
            return new Range(currentNumber, currentRange);
        } else {
            throw new SearchCompiler.ParseError(errorMessage);
        }
    }

    public String getText() {
        return currentText;
    }
}