package json;

import json.JSON.Token.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class JSON
{
    /**
     * Takes a string representing a JSON file, and parses it into a Token that the user can manipulate. The returned
     * Token represents the JSON object or array.
     * @param contents The String with the contents of the JSON file.
     * @return A Token representing the JSON object/array.
     */
    public static Token parse(String contents)
    {
        try
        {
            return new Parser(contents).parse();
        }
        catch(ParseException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Takes a JSON Token and returns the String representation of the object or array. The returned String is formatted
     * to be writeable to a file and parsed again.
     * @param json The Token of the JSON object/array.
     * @return A String with the compiled contents of the JSON object/array.
     */
    public static String compile(Token json)
    {
        return new Compiler(json).compile();
    }

    /**
     * Reads the contents of a Path, and attempts to create a Token representation of the contents. Works similar to the
     * parse method, however also including reading the contents of the file into a String to parse.
     * @param filePath The Path leading to the contents.
     * @return A Token representing the JSON object/array.
     */
    public static Token read(Path filePath)
    {
        try
        {
            return parse(Files.readString(filePath));
        }
        catch(IOException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Writes the passed Token to the Path, as a JSON file. Works similar to the write method, however also writes the
     * compiled String to the provided Path.
     * @param json The Token to compile and write.
     * @param filePath The Path to write to.
     */
    public static void write(Token json, Path filePath)
    {
        try
        {
            Files.writeString(filePath, compile(json));
        }
        catch(IOException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Converts a List of any type into a List of Tokens, easier to be used in conjunction with the parser.
     * @param array The List to convert.
     * @return A List of Tokens.
     * @param <T> The original type for the List.
     */
    public static <T> List<Token> convertArray(List<T> array)
    {
        List<Token> tokens = new ArrayList<>();

        for(T t : array)
        {
            switch(t)
            {
                case Number num -> tokens.add(new Token(num));
                case String str -> tokens.add(new Token(str));
                case Boolean bol -> tokens.add(new Token(bol));
                case null -> tokens.add(new Token());
                case List<?> arr -> tokens.add(new Token(convertArray(arr)));
                case Map<?, ?> obj -> tokens.add(new Token(obj));
                case Token token -> tokens.add(token);
                default -> tokens.add(new Token(t.toString()));
            }
        }

        return tokens;
    }

    /**
     * Converts a Map of any types into a Map of Strings to Tokens, easier to use in conjunction with the parser.
     * @param map The Map or any types to convert.
     * @return A Map of Strings to Tokens.
     * @param <K> The original type for the Map keys.
     * @param <V> The original type for the Map values.
     */
    public static <K, V> Map<String, Token> convertObject(Map<K, V> map)
    {
        Map<String, Token> tokens = new HashMap<>();

        for(Entry<K, V> entry : map.entrySet())
        {
            String key = entry.getKey().toString();
            V value = entry.getValue();

            switch(value)
            {
                case Number num -> tokens.put(key, new Token(num));
                case String str -> tokens.put(key, new Token(str));
                case Boolean bol -> tokens.put(key, new Token(bol));
                case null -> tokens.put(key, new Token());
                case List<?> arr -> tokens.put(key, new Token(convertArray(arr)));
                case Map<?, ?> obj -> tokens.put(key, new Token(obj));
                case Token token -> tokens.put(key, token);
                default -> tokens.put(key, new Token(value.toString()));
            }
        }

        return tokens;
    }

    private static class Parser
    {
        private static final Set<Character> NUMBER_SET = Set.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', 'e', '.');

        private final String contents;
        private int index;
        private int count;

        public Parser(String contents)
        {
            this.contents = contents;
            this.index = 0;
            this.count = 0;
        }

        public Token parse() throws ParseException
        {
            Token token = this.getNext();
            while(token == null && this.index < this.contents.length())
                token = this.getNext();

            return token;

//            return this.getNext();
        }

        public Token getNext() throws ParseException
        {
            Token token;

            token = this.attemptNumber();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            token = this.attemptString();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            token = this.attemptBoolean();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            token = this.attemptNull();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            token = this.attemptArray();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            token = this.attemptObject();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            if(this.canSkip())
            {
                this.index++;
                return null;
            }

            throw new RuntimeException("Failed to get next @ " + this.index);
        }

        private boolean canSkip()
        {
            char current = this.contents.charAt(this.index);
            return Character.isWhitespace(current) || current == ',' || current == ':';
        }

        private Token attemptNumber() throws ParseException
        {
            StringBuilder numberString = new StringBuilder();
//            char currentCharacter = this.contents.charAt(this.index);
//
//            while(NUMBER_SET.contains(currentCharacter))
//            {
//                numberString.append(currentCharacter);
//                this.index++;
//                currentCharacter = this.contents.charAt(this.index);
//            }

            while(this.index < this.contents.length())
            {
                char currentCharacter = this.contents.charAt(this.index);
                if(!NUMBER_SET.contains(currentCharacter))
                    break;

                numberString.append(currentCharacter);
                this.index++;
            }

            if(numberString.isEmpty())
                return null;

            Number number = NumberFormat.getInstance().parse(numberString.toString());
            return new Token(number);
        }

        private Token attemptString()
        {
            char currentCharacter = this.contents.charAt(this.index);
            if(currentCharacter != '"')
                return null;

            this.index++;
            StringBuilder string = new StringBuilder();

            boolean escaping = false;
            currentCharacter = this.contents.charAt(this.index);
//            while(currentCharacter != '"' || (!string.isEmpty() && string.charAt(string.length() - 1) == '\\'))
            while(currentCharacter != '"' || escaping)
            {
                string.append(currentCharacter);
                escaping = !escaping && currentCharacter == '\\';
                this.index++;
                currentCharacter = this.contents.charAt(this.index);
            }

            this.index++;
            return new Token(string.toString());
        }

        private Token attemptBoolean()
        {
            int length = this.contents.length() - this.index;

            if(length >= 4 && this.contents.startsWith("true", this.index))
            {
                this.index += 4;
                return new Token(true);
            }
            if(length >= 5 && this.contents.startsWith("false", this.index))
            {
                this.index += 5;
                return new Token(false);
            }

            return null;
        }

        private Token attemptNull()
        {
            int length = this.contents.length() - this.index;

            if(length >= 4 && this.contents.startsWith("null", this.index))
            {
                this.index += 4;
                return new Token();
            }

            return null;
        }

        private Token attemptArray() throws ParseException
        {
            if(this.contents.charAt(this.index) != '[')
                return null;

            this.index++;
            List<Token> array = new ArrayList<>();

            while(this.contents.charAt(this.index) != ']')
            {
                Token token = this.getNext();
                if(token != null)
                    array.add(token);
            }

            this.index++;
            return new Token(array);
        }

        private Token attemptObject() throws ParseException
        {
            if(this.contents.charAt(this.index) != '{')
                return null;

            this.index++;
            Builder builder = new Builder();
            while(this.contents.charAt(this.index) != '}')
            {
                Token token = this.getNext();
                if(token != null)
                    builder.queue(token);
            }

            this.index++;
            return new Token(builder.complete());
        }
    }

    private static class Compiler
    {
        private final Token token;
        private final String space;

        public Compiler(Token token)
        {
            this.token = token;
            this.space = "  ";
        }

        public String compile()
        {
            return toString(this.token, "");
        }

        private String toString(Token token, String indent)
        {
            return switch(token.type)
            {
                case NULL -> "null";
                case NUMBER -> token.num.toString();
                case STRING -> "\"" + token.str + "\"";
                case BOOLEAN -> token.bol.toString();
                case ARRAY -> formatArray(token, indent + this.space, token.wrap);
                case OBJECT -> formatObject(token, indent + this.space, token.wrap);
            };
        }

        private String formatArray(Token token, String indent, boolean wrap)
        {
            List<Token> array = token.arr;

            if(array.isEmpty())
                return "[]";

            StringBuilder total = new StringBuilder(wrap ? "[\n" : "[");

            if(wrap)
            {
                for(Token string : array)
                    total.append(indent).append(this.toString(string, indent)).append(",\n");
                total.deleteCharAt(total.length() - 2).append(indent).delete(total.length() - this.space.length(), total.length()).append("]");
            }
            else
            {
                for(Token string : array)
                    total.append(this.toString(string, indent)).append(", ");
                total.delete(total.length() - 2, total.length()).append("]");
            }

            return total.toString();
        }

        private String formatObject(Token token, String indent, boolean wrap)
        {
            Map<String, Token> object = token.obj;

            if(object.isEmpty())
                return "{}";

            StringBuilder total = new StringBuilder(wrap ? "{\n" : "{");

            Comparator<Entry<String, Token>> comparator;

            if(token.comparator == null)
                comparator = Comparator.comparingInt(entry -> entry.getValue().order);
            else
                comparator = (a, b) -> token.comparator.compare(a.getValue(), b.getValue());

            List<Entry<String, Token>> sorted = object.entrySet().stream().sorted(comparator).toList();

            if(wrap)
            {
                for(Entry<String, Token> field : sorted)
                    total.append(indent).append("\"").append(field.getKey()).append("\": ").append(this.toString(field.getValue(), indent)).append(",\n");
                total.deleteCharAt(total.length() - 2).append(indent).delete(total.length() - this.space.length(), total.length()).append("}");
            }
            else
            {
                for(Entry<String, Token> field : sorted)
                    total.append("\"").append(field.getKey()).append("\": ").append(this.toString(field.getValue(), indent)).append(", ");
                total.delete(total.length() - 2, total.length()).append("}");
            }

            return total.toString();
        }
    }

    /**
     * The Token class represents every field a JSON can have. Since JSON doesn't specify types, the type
     * is decided when parsing, and as such must be represented by a generic class that can represent all types. The
     * Token class is used with all values in the JSON, and can be converted into any of the types in a JSON although
     * converting into the wrong type will throw an exception. For ease of use, Tokens representing objects or arrays
     * have some methods to directly interact with the underlying Map or List to reduce the number of method calls and
     * conversions, however using these methods on a Token that doesn't represent an object or array will also throw an
     * exception.
     */
    public static class Token
    {
        private Number num;
        private String str;
        private Boolean bol;
        private List<Token> arr;
        private Map<String, Token> obj;

        /**
         * An enum to represent all the different types a JSON field can represent.
         */
        public enum Type
        {NULL, NUMBER, STRING, BOOLEAN, ARRAY, OBJECT}

        private Type type;

        private int order;
        private boolean wrap;
        private Comparator<Token> comparator;

        private String name;

        /**
         * Creates a null Token.
         */
        public Token()
        {
            this.set();

            this.order = 0;
            this.wrap = true;

            this.name = null;
        }

        /**
         * Creates a Token representing a number.
         *
         * @param num The number the Token represents.
         */
        public Token(Number num)
        {
            this.set(num);

            this.order = 0;
            this.wrap = true;

            this.name = null;
        }

        /**
         * Creates a Token representing a String.
         *
         * @param str The String the Token represents.
         */
        public Token(String str)
        {
            this.set(str);

            this.order = 0;
            this.wrap = true;

            this.name = null;
        }

        /**
         * Creates a Token representing a boolean.
         *
         * @param bol The boolean the Token represents.
         */
        public Token(Boolean bol)
        {
            this.set(bol);

            this.order = 0;
            this.wrap = true;

            this.name = null;
        }

        /**
         * Creates a Token representing an array.
         * @param arr The List to represent. The List is automatically converted to a List of Tokens.
         */
        public Token(List<?> arr)
        {
            this.set(arr);

            this.order = 0;
            this.wrap = false;

            this.name = null;
        }

        /**
         * Creates a Token representing an object.
         * @param obj The Map to represent. The Map is automatically converted to a Map of Strings to Tokens.
         */
        public Token(Map<?, ?> obj)
        {
            this.set(obj);

            this.order = 0;
            this.wrap = true;

            this.name = null;
        }

        /**
         * Creates a new Token to represent the given object. If the given object is an instance of a data type that can
         * be represented in the JSON, then it is cast that before adding to ensure the correct data type is
         * represented. If the passed object is a Token, then this is set to the value of the passed Token. Otherwise,
         * if the passed object doesn't represent any of the supported data types, it is instead passed using the
         * built-in toString() method, and is represented as a String.
         * @param object The object to represent.
         */
        public Token(Object object)
        {
            this.set(object);

            this.order = 0;
            this.wrap = true;

            this.name = null;
        }

        /**
         * Creates a new Token to represent the given Serial object. The Serial interface is intended to simplify the
         * code needed to convert an Object into a Token. Any class implementing the Serial interface requires the
         * implementation of the toToken() method, which this constructor then uses. Simply put, this provides a
         * shortcut to convert an object to a Token.
         * @param serial The Serial object to create a Token of.
         */
        public Token(Serial serial)
        {
            this.set(serial);

            this.order = 0;
            this.wrap = true;

            this.name = null;
        }

        /**
         * Gets the number this Token represents. Throws an exception if the Token doesn't represent a number.
         *
         * @return The Number the Token represents.
         */
        public Number getNumber()
        {
            this.checkType(Type.NUMBER);
            return this.num;
        }

        /**
         * Gets the number the Token represents as a byte. Throws an exception if the Token doesn't represent a number.
         *
         * @return The byte the Token's Number represents.
         */
        public byte getByte()
        {
            this.checkType(Type.NUMBER);
            if(this.num != null)
                return this.num.byteValue();
            return 0;
        }

        /**
         * Gets the number the Token represents as a short. Throws an exception if the Token doesn't represent a number.
         *
         * @return The short the Token's Number represents.
         */
        public short getShort()
        {
            this.checkType(Type.NUMBER);
            if(this.num != null)
                return this.num.shortValue();
            return 0;
        }

        /**
         * Gets the number the Token represents as an integer. Throws an exception if the Token doesn't represent a
         * number.
         *
         * @return The integer the Token's Number represents.
         */
        public int getInteger()
        {
            this.checkType(Type.NUMBER);
            if(this.num != null)
                return this.num.intValue();
            return 0;
        }

        /**
         * Gets the number the Token represents as a long. Throws an exception if the Token doesn't represent a number.
         *
         * @return The long the Token's Number represents.
         */
        public long getLong()
        {
            this.checkType(Type.NUMBER);
            if(this.num != null)
                return this.num.longValue();
            return 0;
        }

        /**
         * Gets the number the Token represents as a float. Throws an exception if the Token doesn't represent a number.
         *
         * @return The float the Token's number represents.
         */
        public float getFloat()
        {
            this.checkType(Type.NUMBER);
            if(this.num != null)
                return this.num.floatValue();
            return 0;
        }

        /**
         * Gets the number the Token represents as a double. Throws an exception if the Token doesn't represent a
         * number.
         *
         * @return The double the Token's number represents.
         */
        public double getDouble()
        {
            this.checkType(Type.NUMBER);
            if(this.num != null)
                return this.num.doubleValue();
            return 0;
        }

        /**
         * Gets the String this Token represents. Throws an exception if the Token doesn't represent a String.
         *
         * @return The String the Token represents.
         */
        public String getString()
        {
            this.checkType(Type.STRING);
            return this.str;
        }

        /**
         * Gets the boolean this Token represents. Throws an exception if the Token doesn't represent a boolean.
         *
         * @return The Boolean the Token represents.
         */
        public Boolean getBoolean()
        {
            this.checkType(Type.BOOLEAN);
            return this.bol;
        }

        /**
         * Gets the List this Token represents. Throws an exception if the Token doesn't represent a List.
         *
         * @return The List of Tokens the Token represents.
         */
        public List<Token> getArray()
        {
            this.checkType(Type.ARRAY);
            return this.arr;
        }

        /**
         * Gets the List this Token represents with each element represented as a Number. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a Number.
         *
         * @return The List of Numbers the Token represents.
         */
        public List<Number> getNumberArray()
        {
            this.checkType(Type.ARRAY);

            List<Number> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getNumber());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a Byte. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a Byte.
         *
         * @return The List of Bytes the Token represents.
         */
        public List<Byte> getByteArray()
        {
            this.checkType(Type.ARRAY);

            List<Byte> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getByte());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a Short. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a Short.
         *
         * @return The List of Shorts the Token represents.
         */
        public List<Short> getShortArray()
        {
            this.checkType(Type.ARRAY);

            List<Short> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getShort());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as an Integer. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent an Integer.
         *
         * @return The List of Integers the Token represents.
         */
        public List<Integer> getIntegerArray()
        {
            this.checkType(Type.ARRAY);

            List<Integer> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getInteger());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a Long. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a Long.
         *
         * @return The List of Longs the Token represents.
         */
        public List<Long> getLongArray()
        {
            this.checkType(Type.ARRAY);

            List<Long> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getLong());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a Float. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a Float.
         *
         * @return The List of Floats the Token represents.
         */
        public List<Float> getFloatArray()
        {
            this.checkType(Type.ARRAY);

            List<Float> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getFloat());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a Double. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a Double.
         *
         * @return The List of Doubles the Token represents.
         */
        public List<Double> getDoubleArray()
        {
            this.checkType(Type.ARRAY);

            List<Double> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getDouble());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a String. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a String.
         *
         * @return The List of Strings the Token represents.
         */
        public List<String> getStringArray()
        {
            this.checkType(Type.ARRAY);

            List<String> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getString());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a Boolean. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a Boolean.
         *
         * @return The List of Booleans the Token represents.
         */
        public List<Boolean> getBooleanArray()
        {
            this.checkType(Type.ARRAY);

            List<Boolean> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getBoolean());
            return list;
        }

        /**
         * Gets the List this Token represents with each element represented as a List. Throws an exception if the
         * Token doesn't represent a List, or an element in the List doesn't represent a List.
         *
         * @return The List of Lists the Token represents.
         */
        public List<List<Token>> getArrayArray()
        {
            this.checkType(Type.ARRAY);

            List<List<Token>> list = new ArrayList<>();
            for(Token token : this.arr)
                list.add(token.getArray());
            return list;
        }

        /**
         * Gets the List this Token represents as an array of Tokens. Throws an exception if the Token doesn't represent
         * a List.
         *
         * @return An array of Tokens this Token represents.
         */
        public Token[] toArray()
        {
            this.checkType(Type.ARRAY);

            Token[] array = new Token[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getToken(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of Numbers. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a Number.
         *
         * @return An array of Numbers this Token represents.
         */
        public Number[] toNumberArray()
        {
            this.checkType(Type.ARRAY);

            Number[] array = new Number[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getNumber(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of bytes. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a byte.
         *
         * @return An array of bytes this Token represents.
         */
        public byte[] toByteArray()
        {
            this.checkType(Type.ARRAY);

            byte[] array = new byte[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getByte(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of shorts. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a short.
         *
         * @return An array of shorts this Token represents.
         */
        public short[] toShortArray()
        {
            this.checkType(Type.ARRAY);

            short[] array = new short[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getShort(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of integers. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent an integer.
         *
         * @return An array of integers this Token represents.
         */
        public int[] toIntegerArray()
        {
            this.checkType(Type.ARRAY);

            int[] array = new int[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getInteger(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of longs. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a long.
         *
         * @return An array of longs this Token represents.
         */
        public long[] toLongArray()
        {
            this.checkType(Type.ARRAY);

            long[] array = new long[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getLong(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of floats. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a float.
         *
         * @return An array of floats this Token represents.
         */
        public float[] toFloatArray()
        {
            this.checkType(Type.ARRAY);

            float[] array = new float[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getFloat(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of doubles. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a double.
         *
         * @return An array of doubles this Token represents.
         */
        public double[] toDoubleArray()
        {
            this.checkType(Type.ARRAY);

            double[] array = new double[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getDouble(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of Strings. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a String.
         *
         * @return An array of Strings this Token represents.
         */
        public String[] toStringArray()
        {
            this.checkType(Type.ARRAY);

            String[] array = new String[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getString(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of booleans. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a boolean.
         *
         * @return An array of booleans this Token represents.
         */
        public boolean[] toBooleanArray()
        {
            this.checkType(Type.ARRAY);

            boolean[] array = new boolean[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getBoolean(i);
            return array;
        }

        /**
         * Gets the List this Token represents as an array of Lists. Throws an exception if the Token doesn't
         * represent a List or an element in the List doesn't represent a List.
         *
         * @return An array of Lists this Token represents.
         */
        public List<Token>[] toArrayArray()
        {
            this.checkType(Type.ARRAY);
            List<Token>[] array = new List[this.arr.size()];
            for(int i = 0; i < this.arr.size(); i++)
                array[i] = this.getArray(i);
            return array;
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a Token. Throws an exception if
         * the Token doesn't represent an array.
         *
         * @param index The index at which to get the element.
         * @return The Token at the specified index.
         */
        public Token getToken(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index);
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a Number. Throws an exception if
         * the Token doesn't represent an array, or the element at the index doesn't represent a number.
         *
         * @param index The index at which to get the element.
         * @return The Number at the specified index.
         */
        public Number getNumber(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getNumber();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a byte. Throws an exception if
         * the Token doesn't represent an array, or the element at the index doesn't represent a number.
         *
         * @param index The index at which to get the element.
         * @return The byte at the specified index.
         */
        public byte getByte(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getByte();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a short. Throws an exception if
         * the Token doesn't represent an array, or the element at the index doesn't represent a number.
         *
         * @param index The index at which to get the element.
         * @return The short at the specified index.
         */
        public short getShort(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getShort();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as an integer. Throws an exception
         * if the Token doesn't represent an array, or the element at the index doesn't represent a number.
         *
         * @param index The index at which to get the element.
         * @return The integer at the specified index.
         */
        public int getInteger(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getInteger();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a long. Throws an exception if
         * the Token doesn't represent an array, or the element at the index doesn't represent a number.
         *
         * @param index The index at which to get the element.
         * @return The long at the specified index.
         */
        public long getLong(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getLong();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a float. Throws an exception if
         * the Token doesn't represent an array, or the element at the index doesn't represent a number.
         *
         * @param index The index at which to get the element.
         * @return The float at the specified index.
         */
        public float getFloat(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getFloat();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a double. Throws an exception if
         * the Token doesn't represent an array, or the element at the index doesn't represent a number.
         *
         * @param index The index at which to get the element.
         * @return The double at the specified index.
         */
        public double getDouble(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getDouble();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a String. Throws an exception if
         * the Token doesn't represent an array, or the element at the index doesn't represent a string.
         *
         * @param index The index at which to get the element.
         * @return The String at the specified index.
         */
        public String getString(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getString();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a Boolean. Throws an exception
         * if the Token doesn't represent an array, or the element at the index doesn't represent a boolean.
         *
         * @param index The index at which to get the element.
         * @return The Boolean at the specified index.
         */
        public Boolean getBoolean(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getBoolean();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a List of Tokens. Throws an
         * exception if the Token doesn't represent an array, or the element at the index doesn't represent an array.
         *
         * @param index The index at which to get the element.
         * @return The List of Tokens at the specified index.
         */
        public List<Token> getArray(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getArray();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a Map of Strings to Tokens.
         * Throws an exception if the Token doesn't represent an array, or the element at the index doesn't represent an
         * object.
         *
         * @param index The index at which to get the element.
         * @return The Map of Strings to Tokens at the specified index.
         */
        public Map<String, Token> getObject(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getObject();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index. Throws an exception if the Token
         * doesn't represent an array.
         *
         * @param index The index at which to get the element.
         * @return The value at the specified index.
         */
        public Object get(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).get();
        }

        /**
         * Treats the Token as an array, and gets the type of the element at the specified index. Throws an exception if
         * the Token doesn't represent an array.
         *
         * @param index The index at which to get the element.
         * @return The type of the element at the specified index.
         */
        public Type getType(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getType();
        }

        /**
         * Treats the Token as an array, and adds the specified Token to the array. Throws an exception if the Token
         * doesn't represent an array.
         *
         * @param token The Token to add to the array.
         */
        public void add(Token token)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(token);
        }

        /**
         * Treats the Token as an array, and adds the specified Number to the array. Throws an exception if the Token
         * doesn't represent an array.
         *
         * @param number The Number to add to the array.
         */
        public void add(Number number)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(new Token(number));
        }

        /**
         * Treats the Token as an array, and adds the specified String to the array. Throws an exception if the Token
         * doesn't represent an array.
         *
         * @param string The String to add to the array.
         */
        public void add(String string)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(new Token(string));
        }

        /**
         * Treats the Token as an array, and adds the specified Boolean to the array. Throws an exception if the Token
         * doesn't represent an array.
         *
         * @param bool The Boolean to add to the array.
         */
        public void add(Boolean bool)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(new Token(bool));
        }

        /**
         * Treats the Token as an array, and adds the specified List of any type to the array. The List is automatically
         * converted to a List of Tokens before adding to the array. Throws an exception if the Token doesn't represent
         * an array.
         *
         * @param array The List to add to the array.
         */
        public void add(List<?> array)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(new Token(array));
        }

        /**
         * Treats the Token as an array, and adds the specified Map of any type to the array. The Map is automatically
         * converted to a Map of Strings to Tokens before adding to the array. Throws an exception if the Token doesn't
         * represent an array.
         *
         * @param object The Map to add to the array.
         */
        public void add(Map<?, ?> object)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(new Token(object));
        }

        /**
         * Treats the Token as an array, and adds the specified Object to the array. The Object is automatically
         * converted to a type the JSON can store, and if the Object doesn't represent anything, then the built-in
         * toString() method is called on the object. Throws an exception if the Token doesn't represent an array.
         * @param object The Object to add to the array.
         */
        public void add(Object object)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(new Token(object));
        }

        /**
         * Treats the Token as an array, and adds the specified Token to the array at the given index. Throws an
         * exception if the Token doesn't represent an array.
         *
         * @param index The index to add the Token at.
         * @param token The Token to add to the array.
         */
        public void add(int index, Token token)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, token);
        }

        /**
         * Treats the Token as an array, and adds the specified Number to the array at the given index. Throws an
         * exception if the Token doesn't represent an array.
         *
         * @param index  The index to add the Token at.
         * @param number The Number to add to the array.
         */
        public void add(int index, Number number)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, new Token(number));
        }

        /**
         * Treats the Token as an array, and adds the specified String to the array at the given index. Throws an
         * exception if the Token doesn't represent an array.
         *
         * @param index  The index to add the Token at.
         * @param string The String to add to the array.
         */
        public void add(int index, String string)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, new Token(string));
        }

        /**
         * Treats the Token as an array, and adds the specified Boolean to the array at the given index. Throws an
         * exception if the Token doesn't represent an array.
         *
         * @param index The index to add the Token at.
         * @param bool  The Boolean to add to the array.
         */
        public void add(int index, Boolean bool)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, new Token(bool));
        }

        /**
         * Treats the Token as an array, and adds the specified List of any type to the array at the given index. The
         * List is automatically converted to a List of Tokens before adding to the array. Throws an exception if the
         * Token doesn't represent an array.
         *
         * @param index The index to add the Token at.
         * @param array The List to add to the array.
         */
        public void add(int index, List<?> array)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, new Token(array));
        }

        /**
         * Treats the Token as an array, and adds the specified Map of any type to the array at the given index. The Map
         * is automatically converted to a Map of Strings to Tokens before adding to the array. Throws an exception if
         * the Token doesn't represent an array.
         *
         * @param index  The index to add the Token at.
         * @param object The Map to add to the array.
         */
        public void add(int index, Map<?, ?> object)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, new Token(object));
        }

        /**
         * Treats the Token as an array, and adds the specified Object to the array at the given index. The Object is
         * automatically converted to a type the JSON can store, and if the Object doesn't represent anything, then the
         * built-in toString() method is called on the object. Throws an exception if the Token doesn't represent an
         * array.
         * @param index The index to add the Token at.
         * @param object The Object to add to the array.
         */
        public void add(int index, Object object)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, new Token(object));
        }

        /**
         * Treats the Token as an array, and sets the element at the specified index to the given Token. Throws an
         * exception if the Token doesn't represent an array.
         * @param index The index to set the Token at.
         * @param token The Token to set the element to.
         */
        public void set(int index, Token token)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).set(token);
        }

        /**
         * Treats the Token as an array, and sets the element at the specified index to the given Number. Throws an
         * exception if the Token doesn't represent an array.
         * @param index The index to set the Token at.
         * @param number The Number to set the element to.
         */
        public void set(int index, Number number)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).set(number);
        }

        /**
         * Treats the Token as an array, and sets the element at the specified index to the given String. Throws an
         * exception if the Token doesn't represent an array.
         * @param index The index to set the Token at.
         * @param string The String to set the element to.
         */
        public void set(int index, String string)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).set(string);
        }

        /**
         * Treats the Token as an array, and sets the element at the specified index to the given Boolean. Throws an
         * exception if the Token doesn't represent an array.
         * @param index The index to set the Token at.
         * @param bool The Boolean to set the element to.
         */
        public void set(int index, Boolean bool)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).set(bool);
        }

        /**
         * Treats the Token as an array, and sets the element at the specified index to the given List of any type. The
         * list is automatically converted into a List of Tokens. Throws an exception if the Token doesn't represent an
         * array.
         * @param index The index to set the Token at.
         * @param array The List to set the element to.
         */
        public void set(int index, List<?> array)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).set(array);
        }

        /**
         * Treats the Token as an array, and sets the element at the specified index to the given Map of any type. The
         * Map is automatically converted into a Map of Strings to Tokens. Throws an exception if the Token doesn't
         * represent an array.
         * @param index The index to set the Token at.
         * @param object The Map to set the element to.
         */
        public void set(int index, Map<?, ?> object)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).set(object);
        }

        /**
         * Treats the Token as an array, and sets the element at the specified index to the given Object. The Object is
         * automatically converted to a type the JSON can store, and if the Object doesn't represent anything, then the
         * built-in toString() method is called on the object. Throws an exception if the Token doesn't represent an
         * array.
         * @param index The index to set the Token at.
         * @param object The Object to add to the array.
         */
        public void set(int index, Object object)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).set(object);
        }

        /**
         * Treats the Token as an array, and removes the Token at the given index. Throws an exception if the Token
         * doesn't represent an array.
         * @param index The index of the Token to remove.
         * @return The Token removed from the array.
         */
        public Token removeToken(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.remove(index);
        }

        /**
         * Treats the Token as an array, and removes the Number at the given index. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the Number to remove.
         * @return The Number removed from the array.
         */
        public Number removeNumber(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.NUMBER);
            return this.arr.remove(index).getNumber();
        }

        /**
         * Treats the Token as an array, and removes the byte at the given index. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the byte to remove.
         * @return The byte removed from the array.
         */
        public byte removeByte(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.NUMBER);
            return this.arr.remove(index).getByte();
        }

        /**
         * Treats the Token as an array, and removes the short at the given index. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the short to remove.
         * @return The short removed from the array.
         */
        public short removeShort(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.NUMBER);
            return this.arr.remove(index).getShort();
        }

        /**
         * Treats the Token as an array, and removes the integer at the given index. If the element being removed
         * doesn't represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an
         * array.
         * @param index The index of the integer to remove.
         * @return The integer removed from the array.
         */
        public int removeInteger(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.NUMBER);
            return this.arr.remove(index).getInteger();
        }

        /**
         * Treats the Token as an array, and removes the long at the given index. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the long to remove.
         * @return The long removed from the array.
         */
        public long removeLong(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.NUMBER);
            return this.arr.remove(index).getLong();
        }

        /**
         * Treats the Token as an array, and removes the float at the given index. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the float to remove.
         * @return The float removed from the array.
         */
        public float removeFloat(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.NUMBER);
            return this.arr.remove(index).getFloat();
        }

        /**
         * Treats the Token as an array, and removes the double at the given index. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the double to remove.
         * @return The double removed from the array.
         */
        public double removeDouble(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.NUMBER);
            return this.arr.remove(index).getDouble();
        }

        /**
         * Treats the Token as an array, and removes the String at the given index. If the element being removed doesn't
         * represent a String, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the String to remove.
         * @return The String removed from the array.
         */
        public String removeString(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.STRING);
            return this.arr.remove(index).getString();
        }

        /**
         * Treats the Token as an array, and removes the Boolean at the given index. If the element being removed
         * doesn't represent a Boolean, then nothing is removed. Throws an exception if the Token doesn't represent an
         * array.
         * @param index The index of the Boolean to remove.
         * @return The Boolean removed from the array.
         */
        public Boolean removeBoolean(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.BOOLEAN);
            return this.arr.remove(index).getBoolean();
        }

        /**
         * Treats the Token as an array, and removes the List of Tokens at the given index. If the element being removed
         * doesn't represent an array, then nothing is remove. Throws an exception if the Token doesn't represent an
         * array.
         * @param index The index of the List to remove.
         * @return The List removed from the array.
         */
        public List<Token> removeArray(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.ARRAY);
            return this.arr.remove(index).getArray();
        }

        /**
         * Treats the Token as an array, and removes the Map of Strings to Tokens at the given index. If the element
         * being removed doesn't represent an array, then nothing is removed. Throws an exception if the Token doesn't
         * represent an array.
         * @param index The index of the Map to remove.
         * @return The Map removed from the array.
         */
        public Map<String, Token> removeObject(int index)
        {
            this.checkType(Type.ARRAY);
            this.arr.get(index).checkType(Type.OBJECT);
            return this.arr.remove(index).getObject();
        }

        /**
         * Treats the Token as an array, and removes the Object at the given index. If the element being removed doesn't
         * represent an array, then nothing is removed. Throws an exception if the Token doesn't represent an array.
         * @param index The index of the Object to remove.
         * @return The Object removed from the array.
         */
        public Object remove(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.remove(index).get();
        }

        /**
         * Gets the Map this Token represents. Throws an exception if the Token doesn't represent a Map.
         *
         * @return The Map of String to Tokens the Token represents.
         */
        public Map<String, Token> getObject()
        {
            this.checkType(Type.OBJECT);
            return this.obj;
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as an Object. Throws an exception if
         * the Token doesn't represent an object.
         *
         * @param field The key at which to get the field.
         * @return The Object at the specified field.
         */
        public Object get(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).get();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a Token. Throws an exception if the
         * Token doesn't represent an object.
         *
         * @param field The key at which to get the field.
         * @return The Token at the specified field.
         */
        public Token getToken(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field);
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a Number. Throws an exception if
         * the Token doesn't represent an object, or the field doesn't represent a number.
         *
         * @param field The key at which to get the field.
         * @return The Number at the specified field.
         */
        public Number getNumber(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getNumber();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a byte. Throws an exception if the
         * Token doesn't represent an object, or the field doesn't represent a number.
         *
         * @param field The key at which to get the field.
         * @return The byte at the specified field.
         */
        public byte getByte(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getByte();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a short. Throws an exception if the
         * Token doesn't represent an object, or the field doesn't represent a number.
         *
         * @param field The key at which to get the field.
         * @return The short at the specified field.
         */
        public short getShort(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getShort();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as an integer. Throws an exception if
         * the Token doesn't represent an object, or the field doesn't represent a number.
         *
         * @param field The key at which to get the field.
         * @return The integer at the specified field.
         */
        public int getInteger(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getInteger();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a long. Throws an exception if the
         * Token doesn't represent an object, or the field doesn't represent a number.
         *
         * @param field The key at which to get the field.
         * @return The long at the specified field.
         */
        public long getLong(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getLong();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a float. Throws an exception if the
         * Token doesn't represent an object, or the field doesn't represent a number.
         *
         * @param field The key at which to get the field.
         * @return The float at the specified field.
         */
        public float getFloat(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getFloat();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a double. Throws an exception if
         * the Token doesn't represent an object, or the field doesn't represent a number.
         *
         * @param field The key at which to get the field.
         * @return The double at the specified field.
         */
        public double getDouble(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getDouble();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a String. Throws an exception if
         * the Token doesn't represent an object, or the field doesn't represent a string.
         *
         * @param field The key at which to get the field.
         * @return The String at the specified field.
         */
        public String getString(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getString();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a Boolean. Throws an exception if
         * the Token doesn't represent an object, or the field doesn't represent a boolean.
         *
         * @param field The key at which to get the field.
         * @return The Boolean at the specified field.
         */
        public Boolean getBoolean(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getBoolean();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a List of Tokens. Throws an
         * exception if the Token doesn't represent an object, or the field doesn't represent an array.
         *
         * @param field The key at which to get the field.
         * @return The List of Tokens at the specified field.
         */
        public List<Token> getArray(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getArray();
        }

        /**
         * Treats the Token as an object, and gets the field at the specified key as a Map of Strings to Tokens. Throws
         * an exception if the Token doesn't represent an object, or the field doesn't represent an object.
         *
         * @param field The key at which to get the field.
         * @return The Map of Strings to Tokens at the specified field.
         */
        public Map<String, Token> getObject(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getObject();
        }

        /**
         * Treats the Token as an object, and gets the type of the field at the specified key. Throws an exception if
         * the Token doesn't represent an object.
         *
         * @param field The key at which to get the field.
         * @return The type of the field at the specified field.
         */
        public Type getType(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getType();
        }

        /**
         * Treats the Token as an object, and checks if the field exists. Throws an exception if the Token doesn't
         * represent an object.
         * @param field The field to check.
         * @return Whether the field exists in the object.
         */
        public boolean has(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.containsKey(field);
        }

        /**
         * Treats the Token as an object, and checks if the field exists as a number. Throws an exception if the Token
         * doesn't represent an object.
         * @param field The field to check.
         * @return Whether the field exists as a number in the object.
         */
        public boolean hasNumber(String field)
        {
            this.checkType(Type.OBJECT);
            Token token = this.obj.get(field);
            return token != null && token.type == Type.NUMBER;
        }

        /**
         * Treats the Token as an object, and checks if the field exists as a string. Throws an exception if the Token
         * doesn't represent an object.
         * @param field The field to check.
         * @return Whether the field exists as a string in the object.
         */
        public boolean hasString(String field)
        {
            this.checkType(Type.OBJECT);
            Token token = this.obj.get(field);
            return token != null && token.type == Type.STRING;
        }

        /**
         * Treats the Token as an object, and checks if the field exists as a boolean. Throws an exception if the Token
         * doesn't represent an object.
         * @param field The field to check.
         * @return Whether the field exists as a boolean in the object.
         */
        public boolean hasBoolean(String field)
        {
            this.checkType(Type.OBJECT);
            Token token = this.obj.get(field);
            return token != null && token.type == Type.BOOLEAN;
        }

        /**
         * Treats the Token as an object, and checks if the field exists as an array. Throws an exception if the Token
         * doesn't represent an object.
         * @param field The field to check.
         * @return Whether the field exists as an array in the object.
         */
        public boolean hasArray(String field)
        {
            this.checkType(Type.OBJECT);
            Token token = this.obj.get(field);
            return token != null && token.type == Type.ARRAY;
        }

        /**
         * Treats the Token as an object, and checks if the field exists as an object. Throws an exception if the Token
         * doesn't represent an object.
         * @param field The field to check.
         * @return Whether the field exists as an object in the object.
         */
        public boolean hasObject(String field)
        {
            this.checkType(Type.OBJECT);
            Token token = this.obj.get(field);
            return token != null && token.type == Type.OBJECT;
        }

        /**
         * Treats the Token as an object, and adds the specified Token to the object. The name of the field representing
         * the Token is provided by the Token's name attribute. Throws an exception if the Token doesn't represent an
         * object, or if the Token's name is null.
         * @param token The token to be added.
         */
        public void put(Token token)
        {
            String name = token.name;

            if(name == null)
                throw new RuntimeException("Cannot put a token into an object without a name for the field");

            this.put(name, token);
        }

        /**
         * Treats the Token as an object, and adds the specified Token to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field The String representing the field name.
         * @param token The Token representing the field value.
         */
        public void put(String field, Token token)
        {
            this.checkType(Type.OBJECT);

            if(this.obj.containsKey(field))
                this.obj.get(field).set(token);
            else
            {
                this.obj.put(field, token);
                this.obj.get(field).name = field;
            }
        }

        /**
         * Treats the Token as an object, and adds the specified Number to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field  The String representing the field name.
         * @param number The Number representing the field value.
         */
        public void put(String field, Number number)
        {
            this.checkType(Type.OBJECT);

            if(this.obj.containsKey(field))
                this.obj.get(field).set(number);
            else
            {
                this.obj.put(field, new Token(number));
                this.obj.get(field).name = field;
            }
        }

        /**
         * Treats the Token as an object, and adds the specified String to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field  The String representing the field name.
         * @param string The String representing the field value.
         */
        public void put(String field, String string)
        {
            this.checkType(Type.OBJECT);

            if(this.obj.containsKey(field))
                this.obj.get(field).set(string);
            else
            {
                this.obj.put(field, new Token(string));
                this.obj.get(field).name = field;
            }
        }

        /**
         * Treats the Token as an object, and adds the specified Boolean to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field The String representing the field name.
         * @param bool  The Token representing the field value.
         */
        public void put(String field, Boolean bool)
        {
            this.checkType(Type.OBJECT);

            if(this.obj.containsKey(field))
                this.obj.get(field).set(bool);
            else
            {
                this.obj.put(field, new Token(bool));
                this.obj.get(field).name = field;
            }
        }

        /**
         * Treats the Token as an object, and adds the specified List of any type to the object. The List is converted
         * automatically into a List of Tokens. Throws an exception if the Token doesn't represent an object.
         *
         * @param field The String representing the field name.
         * @param array The List of any type representing the field value.
         */
        public void put(String field, List<?> array)
        {
            this.checkType(Type.OBJECT);

            if(this.obj.containsKey(field))
                this.obj.get(field).set(array);
            else
            {
                this.obj.put(field, new Token(array));
                this.obj.get(field).name = field;
            }
        }

        /**
         * Treats the Token as an object, and adds the specified Map of any type to the object. The Map is converted
         * automatically into a Map of Strings to Tokens. Throws an exception if the Token doesn't represent an object.
         *
         * @param field  The String representing the field name.
         * @param object The Map of any type representing the field value.
         */
        public void put(String field, Map<?, ?> object)
        {
            this.checkType(Type.OBJECT);

            if(this.obj.containsKey(field))
                this.obj.get(field).set(object);
            else
            {
                this.obj.put(field, new Token(object));
                this.obj.get(field).name = field;
            }
        }

        /**
         * Treats the Token as an object, and adds the specified Object to the object. The Object is automatically
         * converted to a type the JSON can store, and if the Object doesn't represent anything, then the built-in
         * toString() method is called on the object. Throws an exception if the Token doesn't represent an object.
         * @param field The String representing the field name.
         * @param object The Object representing the field value.
         */
        public void put(String field, Object object)
        {
            this.checkType(Type.OBJECT);

            if(this.obj.containsKey(field))
                this.obj.get(field).set(object);
            else
            {
                this.obj.put(field, new Token(object));
                this.obj.get(field).name = field;
            }
        }

        /**
         * Treats the Token as an object, and removes the Token at the given field. Throws an exception if the Token
         * doesn't represent an object.
         * @param field The String representing the name of the field.
         * @return The Token removed from the object.
         */
        public Token removeToken(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.remove(field);
        }

        /**
         * Treats the Token as an object, and removes the Number at the given field. If the element being removed
         * doesn't represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an
         * object.
         * @param field The String representing the name of the field.
         * @return The Number removed from the object.
         */
        public Number removeNumber(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.NUMBER);
            return this.obj.remove(field).getNumber();
        }

        /**
         * Treats the Token as an object, and removes the byte at the given field. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an object.
         * @param field The String representing the name of the field.
         * @return The byte removed from the object.
         */
        public byte removeByte(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.NUMBER);
            return this.obj.remove(field).getByte();
        }

        /**
         * Treats the Token as an object, and removes the short at the given field. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an object.
         * @param field The String representing the name of the field.
         * @return The short removed from the object.
         */
        public short removeShort(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.NUMBER);
            return this.obj.remove(field).getShort();
        }

        /**
         * Treats the Token as an object, and removes the integer at the given field. If the element being removed
         * doesn't represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an
         * object.
         * @param field The String representing the name of the field.
         * @return The integer removed from the object.
         */
        public int removeInteger(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.NUMBER);
            return this.obj.remove(field).getInteger();
        }

        /**
         * Treats the Token as an object, and removes the long at the given field. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an object.
         * @param field The String representing the name of the field.
         * @return The Number removed from the object.
         */
        public long removeLong(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.NUMBER);
            return this.obj.remove(field).getLong();
        }

        /**
         * Treats the Token as an object, and removes the float at the given field. If the element being removed doesn't
         * represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an object.
         * @param field The String representing the name of the field.
         * @return The float removed from the object.
         */
        public float removeFloat(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.NUMBER);
            return this.obj.remove(field).getFloat();
        }

        /**
         * Treats the Token as an object, and removes the double at the given field. If the element being removed
         * doesn't represent a Number, then nothing is removed. Throws an exception if the Token doesn't represent an
         * object.
         * @param field The String representing the name of the field.
         * @return The double removed from the object.
         */
        public double removeDouble(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.NUMBER);
            return this.obj.remove(field).getDouble();
        }

        /**
         * Treats the Token as an object, and removes the String at the given field. If the element being removed
         * doesn't represent a String, then nothing is removed. Throws an exception if the Token doesn't represent an
         * object.
         * @param field The String representing the name of the field.
         * @return The String removed from the object.
         */
        public String removeString(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.STRING);
            return this.obj.remove(field).getString();
        }

        /**
         * Treats the Token as an object, and removes the Boolean at the given field. If the element being removed
         * doesn't represent a Boolean, then nothing is removed. Throws an exception if the Token doesn't represent an
         * object.
         * @param field The String representing the name of the field.
         * @return The Boolean removed from the object.
         */
        public Boolean removeBoolean(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.BOOLEAN);
            return this.obj.remove(field).getBoolean();
        }

        /**
         * Treats the Token as an object, and removes the List of Tokens at the given field. If the element being
         * removed doesn't represent an array, then nothing is removed. Throws an exception if the Token doesn't
         * represent an object.
         * @param field The String representing the name of the field.
         * @return The List removed from the object.
         */
        public List<Token> removeArray(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.ARRAY);
            return this.obj.remove(field).getArray();
        }

        /**
         * Treats the Token as an object, and removes the Map of Strings to Tokens at the given field. If the element
         * being removed doesn't represent an object, then nothing is removed. Throws an exception if the Token doesn't
         * represent an object.
         * @param field The String representing the name of the field.
         * @return The Map removed from the object.
         */
        public Map<String, Token> removeObject(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.get(field).checkType(Type.OBJECT);
            return this.obj.remove(field).getObject();
        }

        /**
         * Gets the value of the Token. The returned value is only specified as an Object because the type is dynamic
         * based on the type in the JSON. So that all values can be represented as an Object, values such as Numbers and
         * Booleans use the wrapper classes. Arrays are represented using a List of Tokens, and objects are represented
         * using a Map of Strings to Tokens.
         *
         * @return The Object representation of the value of the Token.
         */
        public Object get()
        {
            return switch(this.type)
            {
                case NULL -> null;
                case NUMBER -> this.num;
                case STRING -> this.str;
                case BOOLEAN -> this.bol;
                case ARRAY -> this.arr;
                case OBJECT -> this.obj;
            };
        }

        /**
         * Sets the Token to represent null.
         */
        public void set()
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = null;
            this.obj = null;

            this.type = Type.NULL;
        }

        /**
         * Sets the Token to represent a number.
         * @param num The Number to represent.
         */
        public void set(Number num)
        {
            this.num = num;
            this.str = null;
            this.bol = null;
            this.arr = null;
            this.obj = null;

            this.type = Type.NUMBER;
        }

        /**
         * Sets the Token to represent a string.
         * @param str The String to represent.
         */
        public void set(String str)
        {
            this.num = null;
            this.str = str;
            this.bol = null;
            this.arr = null;
            this.obj = null;

            this.type = Type.STRING;
        }

        /**
         * Sets the Token to represent a boolean.
         * @param bol The Boolean to represent.
         */
        public void set(Boolean bol)
        {
            this.num = null;
            this.str = null;
            this.bol = bol;
            this.arr = null;
            this.obj = null;

            this.type = Type.BOOLEAN;
        }

        /**
         * Sets the Token to represent an array. The array is automatically converted to be a List of Tokens.
         * @param arr The List to represent.
         */
        public void set(List<?> arr)
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = convertArray(arr);
            this.obj = null;

            this.type = Type.ARRAY;
        }

        /**
         * Sets the Token to represent an object. The object is automatically converted to be a Map of Strings to Tokens.
         * @param obj The Map to represent.
         */
        public void set(Map<?, ?> obj)
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = null;
            this.obj = convertObject(obj);

            this.type = Type.OBJECT;
        }

        /**
         * Sets the Token to represent the given object. If the given object is an instance of a data type that
         * can be represented in the JSON, then it is cast that before adding to ensure the correct data type is
         * represented. If the passed object is a Token, then this is set to the value of the passed Token. Otherwise,
         * if the passed object doesn't represent any of the supported data types, it is instead passed using the
         * built-in toString() method, and is represented as a String.
         * @param object The object to represent.
         */
        public void set(Object object)
        {
            switch(object)
            {
                case Number x -> this.set(x);
                case String x -> this.set(x);
                case Boolean x -> this.set(x);
                case null -> this.set();
                case List<?> x -> this.set(x);
                case Map<?, ?> x -> this.set(x);
                case Token x -> this.set(x);
                case Serial x -> this.set(x);
                default -> this.set(object.toString());
            }
        }

        /**
         * Sets the Token to be a clone of the provided Token. All current fields within this Token are overridden to
         * the parameter Token's fields. Simply, a cloning method.
         * @param token The Token to clone.
         */
        public void set(Token token)
        {
            this.num = token.num;
            this.str = token.str;
            this.bol = token.bol;
            this.arr = token.arr;
            this.obj = token.obj;

            this.type = token.type;
            this.order = token.order;
            this.wrap = token.wrap;
            this.comparator = token.comparator;

            this.name = token.name;
        }

        /**
         * Sets the Token to be a version of a Serial object. The Serial interface is intended to simplify the code
         * needed to convert an object to a Token, and using this method allows the user to convert a Token to represent
         * the given Serial object.
         * @param serial The object to set this Token to.
         */
        public void set(Serial serial)
        {
            this.set(serial.toToken());
        }

        /**
         * Gets the size of the Token, if it represents an array or an object. Throws an exception if the Token doesn't
         * represent an array or object.
         * @return The size of the Token.
         */
        public int size()
        {
            if(this.type == Type.ARRAY)
                return this.arr.size();

            if(this.type == Type.OBJECT)
                return this.obj.size();

            throw new WrongDataType("Expected " + Type.ARRAY + " or " + Type.OBJECT + ", got " + this.type);
        }

        /**
         * Tests if the Token is empty, if it represents an array or an object. Throws an exception if the Token doesn't
         * represent an array or object.
         * @return Is the Token is empty.
         */
        public boolean isEmpty()
        {
            if(this.type == Type.ARRAY)
                return this.arr.isEmpty();

            if(this.type == Type.OBJECT)
                return this.obj.isEmpty();

            throw new WrongDataType("Expected " + Type.ARRAY + " or " + Type.OBJECT + ", got " + this.type);
        }

        /**
         * Performs the provided action on each element in the array, assuming the Token represents an array. Throws
         * an exception if the Token doesn't represent an array.
         * @param action The action to be performed.
         */
        public void forEach(Consumer<? super Token> action)
        {
            this.checkType(Type.ARRAY);
            this.arr.forEach(action);
        }

        /**
         * Performs the provided action on each element in the object, assuming the Token represents an object. Throws
         * an exception if the Token doesn't represent an object.
         * @param action The action to be performed.
         */
        public void forEach(BiConsumer<? super String, ? super Token> action)
        {
            this.checkType(Type.OBJECT);
            this.obj.forEach(action);
        }

        /**
         * Gets the type of the Token. The type represents the type of data in the JSON.
         *
         * @return the Type.
         */
        public Type getType()
        {
            return this.type;
        }

        /**
         * Gets the order of the Token. The order value determines where in the JSON object the Token is put. While the
         * order makes very little functional difference to the JSON object, it helps maintain readability and
         * consistency when modifying existing JSON files. The fields in a JSON object are sorted by their order, in
         * ascending order. This means lower valued Tokens are placed closer to the top of the JSON object. This is most
         * noticeable when compiling the JSON object.
         *
         * @return The integer representation of the order.
         */
        public int getOrder()
        {
            return this.order;
        }

        /**
         * Sets the order of the Token. The order value determines where in the JSON object the Token is put. While the
         * order makes very little function difference to the JSON object, it helps maintain readability and
         * consistency when modifying existing JSON files. The fields in a JSON object are sorted by their order, in
         * ascending order. This means lower valued Tokens are placed closer to the top of the JSON object. This is most
         * noticeable when compiling the JSON object.
         *
         * @param order The integer representation of the order.
         */
        public void setOrder(int order)
        {
            this.order = order;
        }

        /**
         * Gets whether this Token should wrap when being compiled. This only applies to either arrays or objects, as
         * they can have many fields. If wrapping is set to true, then each field is given its own line in the compiler,
         * and when wrapping is set to false, the fields are placed on the same line. This is purely for the readability
         * of the JSON, and doesn't affect the operation of the JSON.
         * @return Whether the Token should wrap fields on multiple lines.
         */
        public boolean getWrap()
        {
            return this.wrap;
        }

        /**
         * Sets whether this Token should wrap when being compiled. This only applies to either arrays or objects, as
         * they can have many fields. Setting wrapping on a Token that doesn't represent an array or an object will not
         * cause an error, as it shouldn't and doesn't affect them. By setting wrapping to true, all fields within the
         * array or object will be delimited by new lines, placing each field on a new line. When setting wrapping to
         * false, all fields will be placed on the same line. This is purely for the readability of the JSON, as it has
         * no effect on the actual structure of it, only how it looks when read by a human; all new lines are treated as
         * whitespace, and are ignored when parsing the JSON. It is also suggested to recursively set wrapping to the
         * same value, in order to maintain the look for nested Tokens, as this is not taken care of in the compiler for
         * any cases where the user doesn't want a recursive wrapping.
         * @param wrap Whether the Token should wrap fields on multiple lines.
         */
        public void setWrap(boolean wrap)
        {
            this.wrap = wrap;
        }

        /**
         * Gets the Comparator specified for compilation of an object. If the Comparator is null, then the order field
         * will be used during compilation to specify the order of an object's fields. If a Token is parsed, then any
         * contained objects will have a null Comparator, as a native JSON object doesn't have a way to specify an order
         * for fields contained, nor do they usually require a specific order.
         */
        public Comparator<Token> getComparator()
        {
            return this.comparator;
        }

        /**
         * Sets the Comparator used when compiling this token, if this token represents an object. If this token
         * represents an array then the order during compilation will be the same as the array provides. If this token
         * represents anything that isn't an array or an object, then there is no way to interpret the order. When using
         * a Comparator on an object Token, the fields in the object are compiled in the order specified by the
         * given Comparator. Likewise, when parsing a Token, any objects won't have a Comparator provided as the order
         * isn't explicitly defined in a JSON object. Note the order field that all Tokens have as well. The order field
         * is only used if the Comparator is null, and using neither the order nor the Comparator will result in an
         * unspecified order during compilation.
         *
         * @param comparator The Comparator to use during compilation, or null to default to using order.
         */
        public void setComparator(Comparator<Token> comparator)
        {
            this.comparator = comparator;
        }

        /**
         * Gets the name of the Token. The name is the field the Token is found under in the JSON. This only applies if
         * the Token belongs to an object, otherwise if the Token belongs to an array, the name is null.
         * @return The name of the field this Token represents.
         */
        public String getName()
        {
            return this.name;
        }

        /**
         * Creates a String representation of the Token, which includes the type of the Token as well as the value.
         * @return The String representation of the Token.
         */
        @Override
        public String toString()
        {
            if(Boolean.parseBoolean(System.getProperty("json.compileToString", "true")))
                return this.compile();

            return (this.type == null ? "Syntax" : this.type) + ": " + this.get();
        }

        /**
         * Compiles the Token to a formatted JSON that can be written to a file. Shorthand of calling
         * JSON.compile(...) on this Token.
         * @return A String of the compiled Token.
         */
        public String compile()
        {
            return JSON.compile(this);
        }

        /**
         * Creates an object from this Token, provided the object class has a declared constructor accepting only 1
         * Token for a parameter. Throws a RuntimeException wrapping various Reflect exceptions.
         * @param type The class type to convert the object to.
         * @return The object created from this Token.
         * @param <T> The type for the object class.
         */
        public <T> T fromToken(Class<T> type)
        {
            return Serial.fromToken(type, this);
        }

        private void checkType(Type check)
        {
            if(this.type != check)
                throw new WrongDataType("Expected " + check + ", got " + this.type);
        }
    }

    private static class Builder
    {
        private final Map<String, Token> fields;
        private String currentKey;

        public Builder()
        {
            this.fields = new HashMap<>();
            this.currentKey = null;
        }

        public void queue(Token token)
        {
            if(this.currentKey == null)
            {
                if(token.getType() == Type.STRING)
                    this.currentKey = token.getString();
                else
                    throw new RuntimeException("Expected object key, got: " + token);
            }
            else
            {
                token.name = this.currentKey;
                this.fields.put(this.currentKey, token);
                this.currentKey = null;
            }
        }

        public Map<String, Token> complete()
        {
            if(this.currentKey != null)
                throw new RuntimeException("Cannot complete object with unused key");

            return this.fields;
        }
    }

    /**
     * An exception representing attempts to convert JSON Tokens to the wrong type.
     */
    public static class WrongDataType extends RuntimeException
    {
        public WrongDataType(String message)
        {
            super(message);
        }
    }

    /**
     * An interface to allow the JSON parser to be more interoperable with other classes. Any class that implements the
     * Serial interface can be converted to a Token, and read from a Token, allowing for the class to be treated and
     * operated on like any other Token.
     */
    public interface Serial
    {
        /**
         * This method creates a new object constructed from the given Token. If the provided class type doesn't have a
         * matching constructor (i.e a constructor that takes a single Token) then it throws a RuntimeException. It can
         * also throw a RuntimeException representing various issues that using Reflect to instantiate an object can
         * trigger.
         * @param type The class type of the object. The class must have a declared constructor that accepts a single
         *             Token as an argument.
         * @param token The Token to create the object from.
         * @return The object created from the Token.
         * @param <T> The generic type of the created object.
         */
        public static <T> T fromToken(Class<T> type, Token token)
        {
            try
            {
                return type.getDeclaredConstructor(Token.class).newInstance(token);
            }
            catch(Exception exception)
            {
                throw new RuntimeException(exception);
            }
        }

        /**
         * This method should be implemented in such a way that the underlying class can be represented as a JSON Token.
         * When converting the class to a Token, keep in mind that other methods are free to mutate the Token as they
         * see fit, and any changes might affect the representation of the class.
         * @return The Token representing this class.
         */
        public Token toToken();
    }

    /**
     * The Scheme class is designed to provide a way to convert a Token into a scheme. The scheme can be edited using
     * various methods, and populated into a Token.
     */
    public static class Scheme // TODO: Flesh this out. Ensure users can create a Scheme without a Token, and edit.
    {
        private static final Token NUMBER = parse("{\"type\": \"number\"}");
        private static final Token STRING = parse("{\"type\": \"string\"}");
        private static final Token BOOLEAN = parse("{\"type\": \"boolean\"}");

        /**
         * Converts a Token into a Scheme that represents the Token.
         * @param token Token to convert into a Scheme
         * @return Scheme that represents the Token
         */
        public static Scheme convertToScheme(Token token)
        {
            return new Scheme(convert(token));
        }

        private static Token convert(Token token)
        {
            return switch(token.getType())
            {
                case NUMBER -> NUMBER;
                case STRING -> STRING;
                case BOOLEAN -> BOOLEAN;
                case ARRAY -> convertArray(token);
                case OBJECT -> convertObject(token);

                case NULL -> throw new IllegalArgumentException("Cannot include NULL in a scheme");
            };
        }

        private static Token convertArray(Token token)
        {
            List<Token> items = new ArrayList<>();
            token.forEach(item -> items.add(convert(item)));

            Token array = parse("{\"type\": \"array\"}");
            array.put("items", items);

            return array;
        }

        private static Token convertObject(Token token)
        {
            Map<String, Token> properties = new HashMap<>();
            token.forEach((name, property) -> properties.put(name, convert(property)));

            Token object = parse("{\"type\": \"object\"}");
            object.put("properties", properties);

            return object;
        }

        private final Token scheme;

        private Scheme(Token scheme)
        {
            this.scheme = scheme;
        }

        /**
         * Validates a Token against the Scheme.
         * @param token Token to validate
         * @return If the Token fits the Scheme
         */
        public boolean validate(Token token)
        {
            return true; // TODO: This
        }
    }
}