package com.mparticle.lints.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.*
import java.util.*

class MpApiDetectorKt : Detector(), Detector.UastScanner {

    companion object {
        val MESSAGE_MULTIPLE_START_CALLS = "Duplicate call to MParticle.start"
        val MESSAGE_NO_START_CALL_IN_ON_CREATE = "This Method should call MParticle.start()"
        val MESSAGE_START_CALLED_IN_WRONG_PLACE = "MParticle.start() should be called in Application.onCreate(), not here"
        val MESSAGE_NO_START_CALL_AT_ALL = "In order to Initialize MParticle, you need to extend android.app.Application, and call MParticle.start() in it's onCreate() method"

        @JvmStatic
        val ISSUE = Issue.create(
                "MParticleInitialization",
                "mParticle is being started improperly",
                "MParticle.start() is not called in the onCreate method of the Application class",
                Category.MESSAGES,
                7,
                Severity.WARNING,
                Implementation(MpApiDetectorKt::class.java, Scope.JAVA_FILE_SCOPE))

        private val TARGET_METHOD_QUALIFIED_NAME = "com.mparticle.MParticle.start"

        // the deepest level of the AST we want to search to.
        private val MAX_AST_DEPTH = 4
    }

    private var properMethodCall: LocationWrapper? = null
    private var applicationOnCreateCall: LocationWrapper? = null

    private var extraProperMethodCalls = HashSet<LocationWrapper>()
    private var wrongPlaceMethodCalls = HashSet<LocationWrapper>()

    private lateinit var context: JavaContext

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun beforeCheckProject(context: Context) {
        super.beforeCheckProject(context)
        properMethodCall = null
        applicationOnCreateCall = null
        extraProperMethodCalls = HashSet()
        wrongPlaceMethodCalls = HashSet()

    }

    override fun afterCheckProject(context: Context) {
        wrongPlaceMethodCalls.remove(properMethodCall)
        wrongPlaceMethodCalls.removeAll(extraProperMethodCalls)
        extraProperMethodCalls.remove(properMethodCall)

        if (properMethodCall == null) {
            if (applicationOnCreateCall != null) {
                context.report(ISSUE, applicationOnCreateCall!!.location, MESSAGE_NO_START_CALL_IN_ON_CREATE)
            } else {
                context.report(ISSUE, Location.create(context.file), MESSAGE_NO_START_CALL_AT_ALL)
            }
        }
        wrongPlaceMethodCalls.forEach { call ->
            context.report(ISSUE, call.location, MESSAGE_START_CALLED_IN_WRONG_PLACE)
        }
        extraProperMethodCalls.forEach { call ->
            context.report(ISSUE, call.location, MESSAGE_MULTIPLE_START_CALLS)
        }
    }

    /**
     * There are pretty tight limits on how long a lint check is allowed to execute before it is disregarded.
     * To search for API usages of MParticle.start(), we want to:
     * 1) find any Application.onCreate() method bodies, and walk the AST (with a limit on depth) and search for
     * calls to MParticle.start()
     * 2) seach all other method calls, (UMethods) for MParticle.start() calls.
     * 3) after scan- the first MParticle.start() call from within the Application.onCreate() method is the "Proper call". If we find more than one,
     * including the same call made through different code paths, these are "Duplicate calls". Any method we find outside of a code path originating
     * in Application.onCreate() is a "Wrong place call"
     */
    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(method: UMethod) {
                try {
                    this@MpApiDetectorKt.context = context
                    if (isApplicationSubClassOnCreate(context, method)) {
                        findMethodCall(method, TARGET_METHOD_QUALIFIED_NAME, MAX_AST_DEPTH)
                                .apply {
                                    forEach { methodCall ->
                                        if (isTargetMethod(TARGET_METHOD_QUALIFIED_NAME, methodCall)) {
                                            if (properMethodCall == null) {
                                                properMethodCall = LocationWrapper(context.getLocation(methodCall))
                                            } else {
                                                extraProperMethodCalls.add(LocationWrapper(context.getLocation(methodCall)))
                                            }
                                        }
                                    }
                                }
                        applicationOnCreateCall = LocationWrapper(context.getLocation(method))
                    } else {
                        findMethodCall(method, TARGET_METHOD_QUALIFIED_NAME, 1).forEach { methodCall ->
                            if (isTargetMethod(TARGET_METHOD_QUALIFIED_NAME, methodCall)) {
                                wrongPlaceMethodCalls.add(LocationWrapper(context.getLocation(methodCall)))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
        }
    }

    private fun findMethodCall(method: UMethod, targetMethodName: String, depth: Int): List<UCallExpression> {
        fun findMethodCall(element: UElement?, depth: Int): List<UCallExpression> {
            var callExpressions = mutableListOf<UCallExpression>()
            if (depth == 0) {
                return callExpressions
            }

            when (element) {
                is UCallExpression -> {
                    callExpressions.add(element)
                    if (isTargetMethod(targetMethodName, element)) {
                        return callExpressions
                    }
                    element.valueArguments
                            .forEach { a ->
                                callExpressions.addAll(findMethodCall(a, depth))
                            }
                    getMethod(element)?.let {
                        callExpressions.addAll(findMethodCall(it, depth - 1))
                    }
                }
            //Test this, but I think this just applies to kotlin extension blocks, like apply, let, also, etc
                is UBlockExpression -> element.expressions.forEach { e ->
                    //increment depth here, because we are essentially diving into the expression
                    callExpressions.addAll(findMethodCall(e, depth))
                }
                is UDeclarationsExpression ->
                    // This covers the case if there is a method being used to initialize a variable..
                    // i.e int a = random();
                    element.declarations.forEach { declaration ->
                        //out of all the UDeclarationExpressions, the only ones we want to grab the body from are ones that
                        //we know are going to be executed when the original method is called. A UMethod is also a subclass of a
                        //UDeclarationExpression, but we don't want to add this, because it's exitence does not mean it is going
                        //to be invoked. For Methods, we will parse them when their MethodCall is found
                        (declaration as? UVariable)?.
                                apply {
                                    //we dont want to investigate these right now, because their initializer won't be invoked unless it is called
                                    with(" ${text.substring(0, text.indexOf(name ?: ""))} ") {
                                        if (this.contains(" fun ")) {
                                            return@apply
                                        }
                                    }
                                    uastInitializer?.let {
                                        callExpressions.addAll(findMethodCall(it, depth))
                                    }
                                }
                    }
                is UQualifiedReferenceExpression -> {
                    // no need to increment depth for either of these calls, we are splitting a declaration apart, not going down a level
                    callExpressions.addAll(findMethodCall(element.receiver, depth))
                    callExpressions.addAll(findMethodCall(element.selector, depth))
                }
                is ULambdaExpression -> {
                    callExpressions.addAll(findMethodCall(element.body, depth))
                }
                is UReturnExpression -> {
                    callExpressions.addAll(findMethodCall(element.returnExpression, depth))
                }
            }
            return callExpressions
        }

        return findMethodCall(method.uastBody, depth)
    }


    /**
     * This method takes a method call (UCallExpression) and returns it's method body, so we can
     * drill down into it.
     *
     * Especially with local functions, introduced in kotlin, we have to check both whether is method
     * call refers to a local function, or a class level function,
     */
    private fun getMethod(callExpression: UCallExpression): UExpression? {
        return getLocalMethodImplmentation(callExpression) ?: getMethodImplementation(callExpression)
    }

    private fun getLocalMethodImplmentation(callExpression: UCallExpression): UExpression? {
        var methodName = callExpression.methodName
        (callExpression.uastParent as? UBlockExpression)?.
                apply {
                    for (expression in expressions) {
                        if (expression.equals(callExpression)) {
                            //this means that we were unable to find the local function we were looking for, before reaching back to the original function method call
                            return null;
                        }
                        //find if it matches the name (the variable that is), then look at the UAST initializer, that should be where the method calls are, then add oall the mthod calls to the List to be returneed
                        (expression as? UDeclarationsExpression)?.
                                apply {
                                    for (uDeclaration in declarations) {
                                        (uDeclaration as UVariable)?.
                                                apply {
                                                    if (methodName.equals(name)) {
                                                        return uastInitializer
                                                    }
                                                }
                                    }
                                }
                    }
                }
        return null
    }

    /**
     *
     */
    private fun getMethodImplementation(callExpression: UCallExpression): UExpression? {
        return callExpression.resolve()?.
                let {
                    context.uastContext.getMethod(it).uastBody
                }
    }

    private fun isTargetMethod(targetMethodName: String, element: UCallExpression): Boolean {
        //before we resolve the method, do a quick check to see if the name matches
        if (targetMethodName.endsWith(element.methodName ?: "")) {
            //if the name matches, do the more expensive operation of resolving the method implementation,
            //and check the full qualified name
            element.resolve()?.containingClass?.let {
                return targetMethodName.equals("${context.uastContext.getClass(it).qualifiedName}.${element.methodName}")
            }
        }
        return false
    }

    private fun isApplicationSubClassOnCreate(context: JavaContext, method: UMethod): Boolean {
        return isApplicationSubClass(context, method) && method.name.equals("onCreate")
    }

    private fun isApplicationSubClass(context: JavaContext, method: UMethod): Boolean {
        val evaluator = context.evaluator
        return PsiTreeUtil.getParentOfType(method, PsiClass::class.java, true)?.
                let {
                    var isApplicationSubclass = evaluator.extendsClass(it, "android.app.Application", false)
                    var isApplicationClass = method.containingClass?.qualifiedName.equals("android.app.Application") ?: false
                    isApplicationSubclass && !isApplicationClass
                } ?: false
    }

    /**
     * The simplest way I've found to compare whether we are dealing with a call we have already seen
     * before or not, is to compare their location. The Location object does not have an effective "equals()"
     * method, so this class provides us with a way to compare locations
     */
    internal class LocationWrapper constructor(val location: Location) : Comparable<LocationWrapper> {

        override fun hashCode(): Int {
            return toString().hashCode()
        }

        override fun equals(o: Any?): Boolean {
            if (o === this) {
                return true
            }
            if (o is LocationWrapper) {
                val compareTo = o.location
                if (compareTo === location) {
                    return true
                }
                return if (compareTo == null || location == null) {
                    false
                } else compareTo.file.getAbsolutePath() == location.file.getAbsolutePath() &&
                        compareLocation(compareTo.start, location.start) &&
                        compareLocation(compareTo.end, location.end)
            }
            return false
        }

        fun compareLocation(l1: Position?, l2: Position?): Boolean {
            return l1?.column == l2?.column &&
                    l1?.line == l2?.line &&
                    l1?.offset == l2?.offset
        }

        override fun toString(): String {
            return location!!.file.getAbsolutePath() + "\n" +
                    location.start?.offset + " " + location.start?.line + " " + location.start?.column +
                    location.end?.offset + " " + location.end?.line + " " + location.end?.column
        }

        override fun compareTo(other: LocationWrapper): Int {
            return toString().compareTo(other.toString())
        }

    }
}
