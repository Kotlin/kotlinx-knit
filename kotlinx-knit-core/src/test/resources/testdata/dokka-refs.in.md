There are the following ways to refer to functions:
* [captureOutput] is simple but may be ambiguous with a class name.
* [captureOutput()] to disambiguate a function.
* [_captureOutput] to disambiguate the first lowercase letter.
* [kotlinx.knit.test.captureOutput] is using a fully-qualified name. 

For extension functions:
* [verifyOutputLines], [verifyOutputLines()], [_verifyOutputLines] are all valid short links.
* [List.verifyOutputLines] a short name of the receiver class can be used.
* [kotlin.collections.List.verifyOutputLines] a fully qualified name of the receiver class.

For classes:
* [ComputedLinesDiff] is a simple reference.
* [kotlinx.knit.test.ComputedLinesDiff] is a fully qualified reference.

<!--- MODULE /kotlinx-knit-test -->
<!--- INDEX kotlinx.knit.test -->
<!--- END -->
