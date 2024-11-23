// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.github.suhasdotcom.tig.TigGitVcs
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProcessEventListener
import com.intellij.openapi.vcs.VcsEnvCustomizer
import com.intellij.openapi.vcs.VcsEnvCustomizer.VcsExecutableContext
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EnvironmentUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.ThrowableConsumer
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitVcs
import git4idea.config.GitExecutable
import git4idea.config.GitExecutableContext
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersionSpecialty
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * A handler for enc commands
 */
abstract class EncHandler protected constructor(private val myProject: Project?,
                                                directory: File,
                                                val executable: GitExecutable,
                                                val command: GitCommand,
                                                configParameters: List<String?>) {
    /**
     * See [GitImplBase.run]
     */
    /**
     * See [GitImplBase.run]
     */
    var isPreValidateExecutable = true
    /**
     * See [GitImplBase.run]
     */
    /**
     * See [GitImplBase.run]
     */
    var isEnableInteractiveCallbacks = true
    protected val myCommandLine: GeneralCommandLine
    private val myCustomEnv: MutableMap<String, String?> = HashMap()
    protected var myProcess: Process? = null
    /**
     * @return true if standard output is not copied to the console
     */
    /**
     * Set flag specifying if stdout should be copied to the console
     *
     * @param stdoutSuppressed true if output is not copied to the console
     */
    var isStdoutSuppressed // If true, the standard output is not copied to version control console
            : Boolean
    /**
     * @return true if standard output is not copied to the console
     */
    /**
     * Set flag specifying if stderr should be copied to the console
     *
     * @param stderrSuppressed true if error output is not copied to the console
     */
    var isStderrSuppressed = false // If true, the standard error is not copied to version control console
    private var myInputProcessor: ThrowableConsumer<in OutputStream?, IOException>? = null // The processor for stdin
    private val myListeners = EventDispatcher.create(ProcessEventListener::class.java)
    protected var mySilent // if true, the command execution is not logged in version control view
            : Boolean
    private val myExecutableContext: GitExecutableContext
    private var myStartTime: Long = 0 // enc execution start timestamp

    /**
     * A constructor
     *
     * @param project   a project
     * @param directory a process directory
     * @param command   a command to execute
     */
    protected constructor(project: Project?,
                          directory: File,
                          command: GitCommand,
                          configParameters: List<String?>) : this(project,
            directory,
            GitExecutableManager.getInstance().getExecutable(project),
            command,
            configParameters)

    /**
     * A constructor
     *
     * @param project a project
     * @param vcsRoot a process directory
     * @param command a command to execute
     */
    protected constructor(project: Project?,
                          vcsRoot: VirtualFile,
                          command: GitCommand,
                          configParameters: List<String?>) : this(project, VfsUtilCore.virtualToIoFile(vcsRoot), command, configParameters)

    protected fun listeners(): ProcessEventListener {
        return myListeners.multicaster
    }

    fun project(): Project? {
        return myProject
    }

    val workingDirectory: File
        get() = myCommandLine.workDirectory
    val executableContext: VcsExecutableContext
        get() = myExecutableContext

    protected fun addListener(listener: ProcessEventListener) {
        myListeners.addListener(listener)
    }

    /**
     * Execute process with lower priority
     */
    fun withLowPriority() {
        myExecutableContext.withLowPriority(true)
    }

    /**
     * Detach enc process from IDE TTY session
     */
    fun withNoTty() {
        myExecutableContext.withNoTty(true)
    }

    fun addParameters(vararg parameters: String) {
        addParameters(Arrays.asList<@NonNls String?>(*parameters))
    }

    fun addParameters(parameters: List<String?>) {
        for (parameter in parameters) {
            myCommandLine.addParameter(escapeParameterIfNeeded(parameter!!))
        }
    }

    private fun escapeParameterIfNeeded(parameter: @NonNls String): String {
        return if (escapeNeeded(parameter)) {
            parameter.replace("\\^".toRegex(), "^^^^")
        } else parameter
    }

    private fun escapeNeeded(parameter: @NonNls String): Boolean {
        return SystemInfo.isWindows && isCmd && parameter.contains("^")
    }

    private val isCmd: Boolean
        private get() = StringUtil.toLowerCase(myCommandLine.exePath).endsWith("cmd") //NON-NLS

    fun addRelativePaths(vararg parameters: FilePath) {
        addRelativePaths(Arrays.asList(*parameters))
    }

    fun addRelativePaths(filePaths: Collection<FilePath?>) {
        for (path in filePaths) {
            myCommandLine.addParameter(VcsFileUtil.relativePath(workingDirectory, path))
        }
    }

    fun addRelativeFiles(files: Collection<VirtualFile?>) {
        for (file in files) {
            myCommandLine.addParameter(VcsFileUtil.relativePath(workingDirectory, file))
        }
    }

    fun addAbsoluteFile(file: File) {
        myCommandLine.addParameter(executable.convertFilePath(file))
    }

    /**
     * End option parameters and start file paths. The method adds `"--"` parameter.
     */
    fun endOptions() {
        myCommandLine.addParameter("--")
    }

    private val isStarted: Boolean
        private get() = myProcess != null
    val isLargeCommandLine: Boolean
        /**
         * @return true if the command line is too big
         */
        get() = myCommandLine.commandLineString.length > VcsFileUtil.FILE_PATH_LIMIT

    /**
     * @return a command line with full path to executable replace to "enc"
     */
    open fun printableCommandLine(): @NlsSafe String? {
        return if (executable.isLocal) {
            unescapeCommandLine(myCommandLine.getCommandLineString("enc")) //NON-NLS
        } else {
            unescapeCommandLine(myCommandLine.getCommandLineString(null))
        }
    }

    private fun unescapeCommandLine(commandLine: String): String {
        return if (escapeNeeded(commandLine)) {
            commandLine.replace("\\^\\^\\^\\^".toRegex(), "^")
        } else commandLine
    }

    var charset: Charset
        get() = myCommandLine.charset
        set(charset) {
            myCommandLine.charset = charset
        }
    var isSilent: Boolean
        get() = mySilent
        /**
         * Set silent mode. When handler is silent, it does not logs command in version control console.
         * Note that this option also suppresses stderr and stdout copying.
         *
         * @param silent a new value of the flag
         * @see .setStderrSuppressed
         * @see .setStdoutSuppressed
         */
        set(silent) {
            mySilent = silent
            if (silent) {
                isStderrSuppressed = true
                isStdoutSuppressed = true
            }
        }

    /**
     * Set processor for standard input. This is a place where input to the enc application could be generated.
     *
     * @param inputProcessor the processor
     */
    fun setInputProcessor(inputProcessor: ThrowableConsumer<in OutputStream?, IOException>?) {
        myInputProcessor = inputProcessor
    }

    /**
     * Add environment variable to this handler
     *
     * @param name  the variable name
     * @param value the variable value
     */
    fun addCustomEnvironmentVariable(name: @NonNls String, value: @NonNls String?) {
        myCustomEnv[name] = value
    }

    fun containsCustomEnvironmentVariable(key: @NonNls String): Boolean {
        return myCustomEnv.containsKey(key)
    }

    @Throws(IOException::class)
    fun runInCurrentThread() {
        try {
            start()
            if (isStarted) {
                try {
                    if (myInputProcessor != null) {
                        myInputProcessor!!.consume(myProcess!!.outputStream)
                    }
                } finally {
                    waitForProcess()
                }
            }
        } finally {
            logTime()
        }
    }

    private fun logTime() {
        if (myStartTime > 0) {
            val time = System.currentTimeMillis() - myStartTime
            if (!TIME_LOG.isDebugEnabled && time > LONG_TIME) {
                LOG.info(formatDurationMessage(time))
            } else {
                TIME_LOG.debug(formatDurationMessage(time))
            }
        } else {
            LOG.debug(String.format("enc %s finished.", command))
        }
    }

    private fun formatDurationMessage(time: Long): String {
        return String.format("enc %s took %s ms. Command parameters: %n%s", command, time, myCommandLine.commandLineString)
    }

    private fun start() {
        check(!(myProject != null && !myProject.isDefault && !myProject.isTrusted())) { "Shouldn't be possible to run a enc command in the safe mode" }
        check(!isStarted) { "The process has been already started" }
        try {
            myStartTime = System.currentTimeMillis()
            val logDirectoryPath = if (myProject != null) GitImplBase.stringifyWorkingDir(myProject.basePath, myCommandLine.workDirectory) else myCommandLine.workDirectory.path
            if (!mySilent) {
                LOG.info("[" + logDirectoryPath + "] " + printableCommandLine())
            } else {
                LOG.debug("[" + logDirectoryPath + "] " + printableCommandLine())
            }
            if (CALL_TRACE_LOG.isDebugEnabled) {
                CALL_TRACE_LOG.debug(Throwable("[" + logDirectoryPath + "] " + printableCommandLine()))
            }
            prepareEnvironment()
            executable.patchCommandLine(this, myCommandLine, myExecutableContext)
            OUTPUT_LOG.debug(String.format("%s %% %s started: %s", command, this.hashCode(), myCommandLine))

            // start process
            myProcess = startProcess()
            startHandlingStreams()
        } catch (pce: ProcessCanceledException) {
            throw pce
        } catch (t: Throwable) {
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                LOG.warn(t) // will surely happen if called during unit test disposal, because the working dir is simply removed then
            }
            myListeners.multicaster.startFailed(t)
        }
    }

    private fun prepareEnvironment() {
        val executionEnvironment = myCommandLine.environment
        executionEnvironment.clear()
        if (executable.isLocal) {
            executionEnvironment.putAll(EnvironmentUtil.getEnvironmentMap())
        }
        executionEnvironment.putAll(executable.getLocaleEnv())
        executionEnvironment.putAll(myCustomEnv)
        executionEnvironment[GitCommand.IJ_HANDLER_MARKER_ENV] = "true"
        if (!shouldSuppressReadLocks() && Registry.`is`("git.use.env.from.project.context")) {
            VcsEnvCustomizer.EP_NAME.forEachExtensionSafe { customizer: VcsEnvCustomizer -> customizer.customizeCommandAndEnvironment(myProject, executionEnvironment, myExecutableContext) }
            executionEnvironment.remove("PS1") // ensure we won't get detected as interactive shell because of faulty customizer
        }
    }

    @Throws(ExecutionException::class)
    protected abstract fun startProcess(): Process?

    /**
     * Start handling process output streams for the handler.
     */
    protected abstract fun startHandlingStreams()

    /**
     * Wait for process
     */
    protected abstract fun waitForProcess()
    override fun toString(): String {
        return myCommandLine.toString()
    }

    //region deprecated stuff
    @Deprecated("")
    private var myExitCode: Int? = null // exit code or null if exit code is not yet available

    @Deprecated("")
    private val myErrors = Collections.synchronizedList(ArrayList<VcsException>())

    /**
     * A constructor for handler that can be run without project association.
     *
     * @param project          optional project
     * @param directory        working directory
     * @param executable       enc executable
     * @param command          enc command to execute
     * @param configParameters list of config parameters to use for this enc execution
     */
    init {
        myCommandLine = GeneralCommandLine()
                .withWorkDirectory(directory)
                .withExePath(executable.exePath)
                .withCharset(StandardCharsets.UTF_8)
        for (parameter in getConfigParameters(myProject, configParameters)) {
            myCommandLine.addParameters("-c", parameter)
        }
        myCommandLine.addParameter(command.name())
        isStdoutSuppressed = true
        mySilent = command.lockingPolicy() != GitCommand.LockingPolicy.WRITE
        val gitVcs = if (myProject != null) TigGitVcs.getInstance(myProject) else null
        val root = VfsUtil.findFileByIoFile(directory, true)
        val executableType = if (executable is Wsl) VcsEnvCustomizer.ExecutableType.WSL else VcsEnvCustomizer.ExecutableType.LOCAL
        myExecutableContext = GitExecutableContext(gitVcs, root, executableType)
    }

    @get:Deprecated("use {@link GitLineHandler}, {@link Git#runCommand(GitLineHandler)} and {@link GitCommandResult}")
    @set:Deprecated("use {@link GitLineHandler}, {@link Git#runCommand(GitLineHandler)} and {@link GitCommandResult}")
    var exitCode: Int
        /**
         * @return exit code for process if it is available
         */
        get() = myExitCode ?: -1
        /**
         * @param exitCode a exit code for process
         */
        protected set(exitCode) {
            if (myExitCode == null) {
                myExitCode = exitCode
            } else {
                LOG.info("Not setting exit code $exitCode, because it was already set to $myExitCode")
            }
        }

    @Deprecated("remove together with {@link EncHandlerUtil}")
    fun runInCurrentThread(postStartAction: Runnable?) {
        try {
            start()
            if (isStarted) {
                postStartAction?.run()
                waitForProcess()
            }
        } finally {
            logTime()
        }
    } //endregion

    companion object {
        protected val LOG = Logger.getInstance(EncHandler::class.java)
        protected val OUTPUT_LOG = Logger.getInstance("#output." + EncHandler::class.java.getName())
        protected val TIME_LOG = Logger.getInstance("#time." + EncHandler::class.java.getName())
        protected val CALL_TRACE_LOG = Logger.getInstance("#call_trace." + EncHandler::class.java.getName())
        private const val LONG_TIME = (10 * 1000).toLong()
        private fun getConfigParameters(project: Project?,
                                        requestedConfigParameters: List<String?>): List<String?> {
            if (project == null || !GitVersionSpecialty.CAN_OVERRIDE_GIT_CONFIG_FOR_COMMAND.existsIn(project)) {
                return emptyList<@NonNls String?>()
            }
            val toPass: MutableList<String?> = ArrayList()
            toPass.add("core.quotepath=false")
            toPass.add("log.showSignature=false")
            toPass.addAll(requestedConfigParameters)
            if (ApplicationManager.getApplication().isUnitTestMode) {
                toPass.add("protocol.file.allow=always")
            }
            return toPass
        }

        /**
         * Tasks executed under [PotemkinProgress.runInBackground] cannot take read lock.
         */
        protected fun shouldSuppressReadLocks(): Boolean {
            return if (ProgressManager.getInstance().progressIndicator is PotemkinProgress) {
                !ApplicationManager.getApplication().isDispatchThread
            } else false
        }

        @Deprecated("")
        private val LAST_OUTPUT_SIZE = 5
    }
}
