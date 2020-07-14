<!--- TEST_NAME LinesStartTest --> 

Here is some explanatory text

```kotlin 
fun main() {
    check(1 == 2) { "The check has failed" }
}
```                         

> You can get the full code [here](test-lines-start/example-lines-start-01.kt).  

<!--- TEST LINES_START 
Exception in thread "main" java.lang.IllegalStateException: The check has failed
-->