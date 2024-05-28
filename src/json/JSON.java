package json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
            if(t instanceof Number num)
                tokens.add(new Token(num));
            else if(t instanceof String str)
                tokens.add(new Token(str));
            else if(t instanceof Boolean bol)
                tokens.add(new Token(bol));
            else if(t == null)
                tokens.add(new Token());
            else if(t instanceof List<?> arr)
                tokens.add(new Token(convertArray(arr)));
            else if(t instanceof Map<?, ?> obj)
                tokens.add(new Token(obj));
            else if(t instanceof Token token)
                tokens.add(token);
            else
                tokens.add(new Token(t.toString()));
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

        for(Map.Entry<K, V> entry : map.entrySet())
        {
            String key = entry.getKey().toString();
            V value = entry.getValue();

            if(value instanceof Number num)
                tokens.put(key, new Token(num));
            else if(value instanceof String str)
                tokens.put(key, new Token(str));
            else if(value instanceof Boolean bol)
                tokens.put(key, new Token(bol));
            else if(value == null)
                tokens.put(key, new Token());
            else if(value instanceof List<?> arr)
                tokens.put(key, new Token(convertArray(arr)));
            else if(value instanceof Map<?, ?> obj)
                tokens.put(key, new Token(obj));
            else if(value instanceof Token token)
                tokens.put(key, token);
            else
                tokens.put(key, new Token(value.toString()));
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
            return this.getNext();
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
            char currentCharacter = this.contents.charAt(this.index);

            while(NUMBER_SET.contains(currentCharacter))
            {
                numberString.append(currentCharacter);
                this.index++;
                currentCharacter = this.contents.charAt(this.index);
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

            currentCharacter = this.contents.charAt(this.index);
            while(currentCharacter != '"' || (!string.isEmpty() && string.charAt(string.length() - 1) == '\\'))
            {
                string.append(currentCharacter);
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
                case ARRAY -> formatArray(token.arr, indent + this.space);
                case OBJECT -> formatObject(token.obj, indent + this.space);
            };
        }

        private String formatArray(List<Token> array, String indent)
        {
            if(array.isEmpty())
                return "[]";

            StringBuilder total = new StringBuilder("[\n");

            for(Token string : array)
                total.append(indent).append(this.toString(string, indent)).append(",\n");
            total.deleteCharAt(total.length() - 2).append(indent).delete(total.length() - this.space.length(), total.length()).append("]");

            return total.toString();
        }

        private String formatObject(Map<String, Token> object, String indent)
        {
            if(object.isEmpty())
                return "{}";

            StringBuilder total = new StringBuilder("{\n");

            List<Map.Entry<String, Token>> sorted = object.entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getValue().order)).toList();

            for(Map.Entry<String, Token> field : sorted)
                total.append(indent).append("\"").append(field.getKey()).append("\": ").append(this.toString(field.getValue(), indent)).append(",\n");
            total.deleteCharAt(total.length() - 2).append(indent).delete(total.length() - this.space.length(), total.length()).append("}");

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

        /**
         * Creates a null Token.
         */
        public Token()
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = null;
            this.obj = null;

            this.type = Type.NULL;

            this.order = 0;
        }

        /**
         * Creates a Token representing a number.
         *
         * @param num The number the Token represents.
         */
        public Token(Number num)
        {
            this.num = num;
            this.str = null;
            this.bol = null;
            this.arr = null;
            this.obj = null;

            this.type = Type.NUMBER;

            this.order = 0;
        }

        /**
         * Creates a Token representing a String.
         *
         * @param str The String the Token represents.
         */
        public Token(String str)
        {
            this.num = null;
            this.str = str;
            this.bol = null;
            this.arr = null;
            this.obj = null;

            this.type = Type.STRING;

            this.order = 0;
        }

        /**
         * Creates a Token representing a boolean.
         *
         * @param bol The boolean the Token represents.
         */
        public Token(Boolean bol)
        {
            this.num = null;
            this.str = null;
            this.bol = bol;
            this.arr = null;
            this.obj = null;

            this.type = Type.BOOLEAN;

            this.order = 0;
        }

        /**
         * Creates a Token representing an array.
         * @param arr The List to represent. The List is automatically converted to a List of Tokens.
         */
        public Token(List<?> arr)
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = convertArray(arr);
            this.obj = null;

            this.type = Type.ARRAY;

            this.order = 0;
        }

        /**
         * Creates a Token representing an object.
         * @param obj The Map to represent. The Map is automatically converted to a Map of Strings to Tokens.
         */
        public Token(Map<?, ?> obj)
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = null;
            this.obj = convertObject(obj);

            this.type = Type.OBJECT;

            this.order = 0;
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

        // TODO: Add other ease of use methods to Array and Object.
        // TODO: Rename array and object getters to simplify names.

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
         * Treats the Token as an array, and gets the element at the specified index. Throws an exception if the Token
         * doesn't represent an array.
         *
         * @param index The index at which to get the element.
         * @return The value at the specified index.
         */
        public Object getAsArray(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).get();
        }

        /**
         * Treats the Token as an array, and gets the element at the specified index as a Token. Throws an exception if
         * the Token doesn't represent an array.
         *
         * @param index The index at which to get the element.
         * @return The Token at the specified index.
         */
        public Token getAsArrayToken(int index)
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
        public Number getAsArrayNumber(int index)
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
        public byte getAsArrayByte(int index)
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
        public short getAsArrayShort(int index)
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
        public int getAsArrayInteger(int index)
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
        public long getAsArrayLong(int index)
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
        public float getAsArrayFloat(int index)
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
        public double getAsArrayDouble(int index)
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
        public String getAsArrayString(int index)
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
        public Boolean getAsArrayBoolean(int index)
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
        public List<Token> getAsArrayArray(int index)
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
        public Map<String, Token> getAsArrayObject(int index)
        {
            this.checkType(Type.ARRAY);
            return this.arr.get(index).getObject();
        }

        /**
         * Treats the Token as an array, and gets the type of the element at the specified index. Throws an exception if
         * the Token doesn't represent an array.
         *
         * @param index The index at which to get the element.
         * @return The type of the element at the specified index.
         */
        public Type getAsArrayType(int index)
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
        public void addAsArray(Token token)
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
        public void addAsArray(Number number)
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
        public void addAsArray(String string)
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
        public void addAsArray(Boolean bool)
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
        public void addAsArray(List<?> array)
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
        public void addAsArray(Map<?, ?> object)
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
        public void addAsArray(int index, Token token)
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
        public void addAsArray(int index, Number number)
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
        public void addAsArray(int index, String string)
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
        public void addAsArray(int index, Boolean bool)
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
        public void addAsArray(int index, List<?> array)
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
        public void addAsArray(int index, Map<?, ?> object)
        {
            this.checkType(Type.ARRAY);
            this.arr.add(index, new Token(object));
        }

        /**
         * Treats the Token as an array, and returns the size of the array. Throws an exception if the Token doesn't
         * represent an array.
         *
         * @return The size of the array.
         */
        public int sizeAsArray()
        {
            this.checkType(Type.ARRAY);
            return this.arr.size();
        }

        /**
         * Treats the Token as an array, and performs the given action on all elements in the array. Throws an exception
         * if the Token doesn't represent an array.
         *
         * @param action The action to be performed.
         */
        public void forEachAsArray(Consumer<? super Token> action)
        {
            this.checkType(Type.ARRAY);
            this.arr.forEach(action);
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
        public Object getAsObject(String field)
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
        public Token getAsObjectToken(String field)
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
        public Number getAsObjectNumber(String field)
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
        public byte getAsObjectByte(String field)
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
        public short getAsObjectShort(String field)
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
        public int getAsObjectInteger(String field)
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
        public long getAsObjectLong(String field)
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
        public float getAsObjectFloat(String field)
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
        public double getAsObjectDouble(String field)
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
        public String getAsObjectString(String field)
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
        public Boolean getAsObjectBoolean(String field)
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
        public List<Token> getAsObjectArray(String field)
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
        public Map<String, Token> getAsObjectObject(String field)
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
        public Type getAsObjectType(String field)
        {
            this.checkType(Type.OBJECT);
            return this.obj.get(field).getType();
        }

        // TODO: Maybe change put methods to modifying existing tokens to keep order.

        /**
         * Treats the Token as an object, and adds null to the object. Throws an exception if the Token doesn't
         * represent an object.
         *
         * @param field The String representing the field name.
         */
        public void putAsObject(String field)
        {
            this.checkType(Type.OBJECT);
            this.obj.put(field, new Token());
        }

        /**
         * Treats the Token as an object, and adds the specified Token to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field The String representing the field name.
         * @param token The Token representing the field value.
         */
        public void putAsObject(String field, Token token)
        {
            this.checkType(Type.OBJECT);
            this.obj.put(field, token);
        }

        /**
         * Treats the Token as an object, and adds the specified Number to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field  The String representing the field name.
         * @param number The Number representing the field value.
         */
        public void putAsObject(String field, Number number)
        {
            this.checkType(Type.OBJECT);
            this.obj.put(field, new Token(number));
        }

        /**
         * Treats the Token as an object, and adds the specified String to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field  The String representing the field name.
         * @param string The String representing the field value.
         */
        public void putAsObject(String field, String string)
        {
            this.checkType(Type.OBJECT);
            this.obj.put(field, new Token(string));
        }

        /**
         * Treats the Token as an object, and adds the specified Boolean to the object. Throws an exception if the Token
         * doesn't represent an object.
         *
         * @param field The String representing the field name.
         * @param bool  The Token representing the field value.
         */
        public void putAsObject(String field, Boolean bool)
        {
            this.checkType(Type.OBJECT);
            this.obj.put(field, new Token(bool));
        }

        /**
         * Treats the Token as an object, and adds the specified List of any type to the object. The List is converted
         * automatically into a List of Tokens. Throws an exception if the Token doesn't represent an object.
         *
         * @param field The String representing the field name.
         * @param array The List of any type representing the field value.
         */
        public void putAsObject(String field, List<?> array)
        {
            this.checkType(Type.OBJECT);
            this.obj.put(field, new Token(array));
        }

        /**
         * Treats the Token as an object, and adds the specified Map of any type to the object. The Map is converted
         * automatically into a Map of Strings to Tokens. Throws an exception if the Token doesn't represent an object.
         *
         * @param field  The String representing the field name.
         * @param object The Map of any type representing the field value.
         */
        public void putAsObject(String field, Map<?, ?> object)
        {
            this.checkType(Type.OBJECT);
            this.obj.put(field, new Token(object));
        }

        /**
         * Treats the Token as an object, and returns the size of the object. Throws an exception if the Token doesn't
         * represent an object.
         *
         * @return The size of the object.
         */
        public int sizeAsObject()
        {
            this.checkType(Type.OBJECT);
            return this.obj.size();
        }

        /**
         * Treats the Token as an object, and performs the given action on all elements in the object. Throws an
         * exception if the Token doesn't represent an object.
         *
         * @param action The action to be performed.
         */
        public void forEachAsObject(BiConsumer<? super String, ? super Token> action)
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
        }

        /**
         * Gets the size of the Token, if it represents an array or an object. Throws an exception if the Token doesn't
         * represent an array or object.
         * @return The size of the Token.
         */
        public int size()
        {
            return switch(this.type)
            {
                case ARRAY -> this.arr.size();
                case OBJECT -> this.obj.size();

                default ->
                        throw new WrongDataType("Expected " + Type.ARRAY + " or " + Type.OBJECT + ", got " + this.type);
            };
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
         * Creates a String representation of the Token, which includes the type of the Token as well as the value.
         * @return The String representation of the Token.
         */
        @Override
        public String toString()
        {
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
                if(token.getType() == Token.Type.STRING)
                    this.currentKey = token.getString();
                else
                    throw new RuntimeException("Expected object key, got: " + token);
            }
            else
            {
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
}