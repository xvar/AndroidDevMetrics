package com.frogermcs.androiddevmetrics.weaving.plugin

import com.android.build.gradle.*
import com.android.build.gradle.api.BaseVariant
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import java.io.File

class AndroidDevMetricsPlugin: Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val hasApp = project.plugins.withType(AppPlugin::class.java).isNotEmpty()
            val hasLib = project.plugins.withType(LibraryPlugin::class.java).isNotEmpty()
            if (!hasApp && !hasLib) {
                throw IllegalStateException("'android' or 'android-library' plugin required.")
            }

            //TODO can we do it as closure?
            project.dependencies.add("releaseImplementation", "com.frogermcs.androiddevmetrics:androiddevmetrics-runtime-noop:0.7")
            project.dependencies.add("debugImplementation", "com.frogermcs.androiddevmetrics:androiddevmetrics-runtime:0.7")
            project.dependencies.add("debugImplementation", "org.aspectj:aspectjrt:1.8.8")
            project.dependencies.add("implementation", "androidx.core:core:1.1.0")
            project.dependencies.add("implementation", "androidx.appcompat:appcompat:1.1.0")
            project.dependencies.add("implementation", "androidx.recyclerview:recyclerview:1.0.0")
            //project.dependencies.add("implementation", "androidx.legacy:legacy-support-v4:26.1.0")

            val log = project.logger
            val variants: DomainObjectSet<BaseVariant>
            if (hasApp) {
                variants = (project.extensions.getByName("android") as AppExtension).applicationVariants as DomainObjectSet<BaseVariant>
            } else {
                variants = (project.extensions.getByName("android") as LibraryExtension).libraryVariants as DomainObjectSet<BaseVariant>
            }

            variants.all { variant ->
                if (!variant.buildType.isDebuggable) {
                    log.debug("Skipping non-debuggable build type '${variant.buildType.name}'.")
                    return@all
                }

                val javaCompiler = variant.javaCompileProvider.get() as AbstractCompile
                javaCompiler.doLast({
                    val args = arrayOf(
                            "-showWeaveInfo",
                            "-1.5",
                            "-inpath", javaCompiler.destinationDir.toString(),
                            "-aspectpath", javaCompiler.classpath.asPath,
                            "-d", javaCompiler.destinationDir.toString(),
                            "-classpath", javaCompiler.classpath.asPath,
                            "-bootclasspath", (project.extensions.getByName("android") as BaseExtension).bootClasspath.joinToString(File.pathSeparator)
                    )
                    log.debug("ajc args: %s".format(args))

                    val handler = MessageHandler(true)
                    Main().run(args, handler)

                    handler.getMessages(null, true).forEach {
                        when(it.kind) {
                            IMessage.ABORT, IMessage.ERROR, IMessage.FAIL -> log.error(it.message, it.thrown)
                            IMessage.WARNING -> log.warn(it.message, it.thrown)
                            IMessage.INFO -> log.info(it.message, it.thrown)
                            IMessage.DEBUG -> log.debug(it.message, it.thrown)
                        }
                    }
                })
            }
        }
    }
}