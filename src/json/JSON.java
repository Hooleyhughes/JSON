package json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

public final class JSON// TODO: Document
{
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

    public static String compile(Token json)
    {
        return new Compiler(json).compile();
    }

    // <editor-fold desc="File IO" default=closed>

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

    // </editor-fold>

    // <editor-fold desc="Conversions" default=closed>

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

    // </editor-fold>

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

            // Attempt number
            token = this.attemptNumber();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            // Attempt string
            token = this.attemptString();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            // Attempt boolean
            token = this.attemptBoolean();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            // Attempt null
            token = this.attemptNull();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            // Attempt array
            token = this.attemptArray();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            // Attempt object
            token = this.attemptObject();
            if(token != null)
            {
                token.setOrder(this.count++);
                return token;
            }

            // Skip whitespace
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

        // <editor-fold desc="Attempts" default=closed>

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

        // </editor-fold>
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
            StringBuilder total = new StringBuilder("[\n");

            for(Token string : array)
                total.append(indent).append(this.toString(string, indent)).append(",\n");
            total.deleteCharAt(total.length() - 2).append(indent).delete(total.length() - this.space.length(), total.length()).append("]");

            return total.toString();
        }

        private String formatObject(Map<String, Token> object, String indent)
        {
            StringBuilder total = new StringBuilder("{\n");

            List<Map.Entry<String, Token>> sorted = object.entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getValue().order)).toList();

            for(Map.Entry<String, Token> field : sorted)
                total.append(indent).append("\"").append(field.getKey()).append("\": ").append(this.toString(field.getValue(), indent)).append(",\n");
            total.deleteCharAt(total.length() - 2).append(indent).delete(total.length() - this.space.length(), total.length()).append("}");

            return total.toString();
        }
    }

    public static class Token
    {
        private final Number num;
        private final String str;
        private final Boolean bol; // Not bool to keep 3 letter names
        private final List<Token> arr;
        private final Map<String, Token> obj;

        public enum Type {NULL, NUMBER, STRING, BOOLEAN, ARRAY, OBJECT}
        private final Type type;

        private int order;

        // <editor-fold desc="Constructors" default=closed>

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

        public <T> Token(List<T> arr)
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = convertArray(arr);
            this.obj = null;

            this.type = Type.ARRAY;

            this.order = 0;
        }

        public <K, V> Token(Map<K, V> obj)
        {
            this.num = null;
            this.str = null;
            this.bol = null;
            this.arr = null;
            this.obj = convertObject(obj);

            this.type = Type.OBJECT;

            this.order = 0;
        }

        // </editor-fold>

        // <editor-fold desc="Get Number" default=closed>

        public Number getNumber()
        {
            return this.num;
        }

        public byte getByte()
        {
            if(this.num != null)
                return this.num.byteValue();
            return 0;
        }

        public short getShort()
        {
            if(this.num != null)
                return this.num.shortValue();
            return 0;
        }

        public int getInteger()
        {
            if(this.num != null)
                return this.num.intValue();
            return 0;
        }

        public long getLong()
        {
            if(this.num != null)
                return this.num.longValue();
            return 0;
        }

        public float getFloat()
        {
            if(this.num != null)
                return this.num.floatValue();
            return 0;
        }

        public double getDouble()
        {
            if(this.num != null)
                return this.num.doubleValue();
            return 0;
        }

        // </editor-fold>

        // <editor-fold desc="Get String" default=closed>

        public String getString()
        {
            return this.str;
        }

        // </editor-fold>

        // <editor-fold desc="Get Boolean" default=closed>

        public Boolean getBoolean()
        {
            return this.bol;
        }

        // </editor-fold>

        // <editor-fold desc="Array" default=closed>

        public List<Token> getArray()
        {
            return this.arr;
        }

        // <editor-fold desc="Get" default=closed>

        public Token getAsArray(int index)
        {
            return this.arr.get(index);
        }

        public Number getAsArrayNumber(int index)
        {
            return this.arr.get(index).getNumber();
        }

        public byte getAsArrayByte(int index)
        {
            return this.arr.get(index).getByte();
        }

        public short getAsArrayShort(int index)
        {
            return this.arr.get(index).getShort();
        }

        public int getAsArrayInteger(int index)
        {
            return this.arr.get(index).getInteger();
        }

        public long getAsArrayLong(int index)
        {
            return this.arr.get(index).getLong();
        }

        public float getAsArrayFloat(int index)
        {
            return this.arr.get(index).getFloat();
        }

        public double getAsArrayDouble(int index)
        {
            return this.arr.get(index).getDouble();
        }

        public String getAsArrayString(int index)
        {
            return this.arr.get(index).getString();
        }

        public Boolean getAsArrayBoolean(int index)
        {
            return this.arr.get(index).getBoolean();
        }

        public List<Token> getAsArrayArray(int index)
        {
            return this.arr.get(index).getArray();
        }

        public Map<String, Token> getAsArrayObject(int index)
        {
            return this.arr.get(index).getObject();
        }

        public Type getAsArrayType(int index)
        {
            return this.arr.get(index).getType();
        }

        // </editor-fold>

        // <editor-fold desc="Add" default=closed>

        public void addAsArray(Token token)
        {
            this.arr.add(token);
        }

        public void addAsArray(Number number)
        {
            this.arr.add(new Token(number));
        }

        public void addAsArray(String string)
        {
            this.arr.add(new Token(string));
        }

        public void addAsArray(Boolean bool)
        {
            this.arr.add(new Token(bool));
        }

        public void addAsArray(List<?> array)
        {
            this.arr.add(new Token(array));
        }

        public void addAsArray(Map<?, ?> object)
        {
            this.arr.add(new Token(object));
        }

        // </editor-fold>

        // </editor-fold>

        // <editor-fold desc="Object" default=closed>

        public Map<String, Token> getObject()
        {
            return this.obj;
        }

        // <editor-fold desc="Get" default=closed>

        public Token getAsObject(String field)
        {
            return this.obj.get(field);
        }

        public Number getAsObjectNumber(String field)
        {
            return this.obj.get(field).getNumber();
        }

        public byte getAsObjectByte(String field)
        {
            return this.obj.get(field).getByte();
        }

        public short getAsObjectShort(String field)
        {
            return this.obj.get(field).getShort();
        }

        public int getAsObjectInteger(String field)
        {
            return this.obj.get(field).getInteger();
        }

        public long getAsObjectLong(String field)
        {
            return this.obj.get(field).getLong();
        }

        public float getAsObjectFloat(String field)
        {
            return this.obj.get(field).getFloat();
        }

        public double getAsObjectDouble(String field)
        {
            return this.obj.get(field).getDouble();
        }

        public String getAsObjectString(String field)
        {
            return this.obj.get(field).getString();
        }

        public Boolean getAsObjectBoolean(String field)
        {
            return this.obj.get(field).getBoolean();
        }

        public List<Token> getAsObjectArray(String field)
        {
            return this.obj.get(field).getArray();
        }

        public Map<String, Token> getAsObjectObject(String field)
        {
            return this.obj.get(field).getObject();
        }

        public Type getAsObjectType(String field)
        {
            return this.obj.get(field).getType();
        }

        // </editor-fold>

        // <editor-fold desc="Put" default=closed>

        public void putAsObject(String field, Token token)
        {
            this.obj.put(field, token);
        }

        public void putAsObject(String field, Number number)
        {
            this.obj.put(field, new Token(number));
        }

        public void putAsObject(String field, String string)
        {
            this.obj.put(field, new Token(string));
        }

        public void putAsObject(String field, Boolean bool)
        {
            this.obj.put(field, new Token(bool));
        }

        public void putAsObject(String field, List<?> array)
        {
            this.obj.put(field, new Token(array));
        }

        public void putAsObject(String field, Map<?, ?> object)
        {
            this.obj.put(field, new Token(object));
        }

        // </editor-fold>

        // </editor-fold>

        // <editor-fold desc="Get Type" default=closed>

        public Type getType()
        {
            return this.type;
        }

        // </editor-fold>

        // <editor-fold desc="Order" default=closed>

        public int getOrder()
        {
            return this.order;
        }

        public void setOrder(int order)
        {
            this.order = order;
        }

        // </editor-fold>

        private Object get()
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

        @Override
        public String toString()
        {
            return (this.type == null ? "Syntax" : this.type) + ": " + this.get();
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
}