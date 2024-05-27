# JSON Parser

This project is a simple JSON parser, designed to read, write and modify JSON files, in Java.

## JSON Structure

JSON objects can contain 8 data types:
- Number
- String
- Boolean
- Null
- Array
- Object

When parsed, the JSON's data is represented as pre-existing Java objects to increase versitility of the parser. JSON Numbers are represented using Java's `java.lang.Number` class. This is a wrapper class that can autobox any form of primitive number in Java (not `char` or `boolean`), however also allows for the value to be `null`. One draw back of using the `Number` class is then converting it back to a primitive data type. JSON Strings are represented using Java's built in `java.lang.String` object. These are self explanatory enough. JSON Booleans are represented with Java's `java.lang.Boolean` class. This class is also a wrapper class for Java's primitive `boolean` data type, and is used in place of the primitive data type to also represent the value as `null`. JSON Null objects aren't representing by anything specific in Java, as Java allows for `null` objects, therefore any JSON fields with a null value are also `null` in Java. JSON Arrays are a little more complicated, as they are represented using Java's `java.util.List<E>` class. Since that Java class requires a type to use, a custon `Token` class is used. JSON Objects are represented as Java's `java.util.Map<K, V>`, with `String` as keys and `Token` as values.

Each field within the JSON object is stored in the Java program as a custom `Token` class. These classes represent any of the values the field can have by having instance fields for each data type except `null`. When getting what data type the token represents, it will throw a runtime exception if the wrong type is called. Each `Token` also has an enum of which data type it is to be able to read without error.
