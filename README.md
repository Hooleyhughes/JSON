# JSON Parser

A small library written in Java for parsing, editing and writing JSON documents. It exposes a simple `Token` tree that mirrors the JSON structure and provides helpers for converting standard Java collections.

## Features
- Parse JSON strings or files into a tree of `Token` objects
- Write `Token` trees back to JSON text
- Utility methods for converting Lists and Maps
- Optional `Serial` interface for mapping custom classes to tokens

## Getting Started
The source consists of a single `JSON.java` file. Compile it with any recent JDK:

```bash
javac -d out src/json/JSON.java
```

### Parsing JSON
```java
String text = "{ \"name\": \"Codex\", \"values\": [1, 2, 3] }";
JSON.Token root = JSON.parse(text);
```

### Reading/Writing Files
```java
Path file = Path.of("data.json");
JSON.Token root = JSON.read(file);      // read from disk
root.put("active", new JSON.Token(true));
JSON.write(root, file);                 // write back to disk
```

### Working with Tokens
Tokens represent the various JSON types. They provide typed getters and setters as well as convenience methods to iterate over arrays and objects.

```java
int firstNumber = root.getToken("values").get(0).getInteger();
root.put("message", new JSON.Token("Hello"));
```

## Future Improvements
Two experimental pieces are included but not fully implemented:

* **Scheme** – a class intended for validating a `Token` against a defined scheme (similar to JSON schema). The skeleton exists but validation logic is incomplete.
* **Serial** – an interface allowing application classes to convert themselves to and from tokens. Implementations can supply `toToken()` and `fromToken()` to integrate with the parser.

Contributions are welcome to flesh out these areas.
