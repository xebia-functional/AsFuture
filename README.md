# AsFuture Compiler plugin

This Kotlin compiler plugin is meant to generate Java friendly APIs based on `suspend` code defined in `commonMain`.
Drawing inspiration from several projects, and techniques out there.

## Usage

```kotlin
class MyService : CoroutineScope by scope, AutoCloseable {
  override val coroutineContext: CoroutineContext = Dispatchers.Default
  
  @AsFuture
  suspend fun example(): Int {
    delay(1000)
    return 42
  }

  override fun close() = runBlocking {
    scope.cancelAndJoin()
  }
}
```

This will generate a `Future` based API for `example`:

```java
class App {
    public static void main(String[] args) {
        MyService service = new MyService();
        service.example()
                .thenAccept(System.out::println);
    }
}
```

Whilst from Kotlin (Multiplatform) `example` will remain our originally defined `suspend` function.
This happens by applying following techniques:

  1. The `example` Java function is generated in `jvmMain`, and uses `CoroutineScope` to launch a `future`.
  2. The newly generated Java function is annotated with `@Deprecated("Only callable from Java", level = DeprecationLevel.HIDDEN)` such that we cannot see this function from the Kotlin API. 
  3. The original `example` function gets `@JvmName` to `exampleSuspend`.

This gives us a Java API that support the _reactive_ nature of `suspend` without having to rely on `runBlocking` (which doesn't exist for JVM),
and we can still turn it into blocking using `get`. Which for Project Loom is _non-blocking_.

By exposing `Future`, we can also easily integrate with other languages on the JVM that support `Future` (e.g. Scala).
