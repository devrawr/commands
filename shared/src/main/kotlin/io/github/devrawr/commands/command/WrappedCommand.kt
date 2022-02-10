package io.github.devrawr.commands.command

import io.github.devrawr.commands.Locale
import io.github.devrawr.commands.command.argument.WrappedArgument
import io.github.devrawr.commands.exception.ArgumentCountException
import io.github.devrawr.commands.exception.ArgumentParseException
import io.github.devrawr.commands.processor.executor.Executor
import io.github.devrawr.commands.processor.help.HelpTopic
import java.lang.reflect.Method

class WrappedCommand(
    val name: Array<String>,
    val method: (Array<Any?>) -> Unit
)
{
    val children = mutableListOf<WrappedCommand>()
    var arguments = mutableListOf<WrappedArgument<*>>()

    val label = name.first()

    var permission: String = ""
    var description: String = "No description"
    var help = false

    val helpTopic =  HelpTopic(this)

    constructor(
        name: Array<String>,
        instance: Any,
        method: Method,
    ) : this(
        name = name,
        method = {
            method.invoke(instance, *it)
        }
    )

    fun formatArguments(
        executor: Executor<*>
    ) = arguments
        .map {
            if (it == arguments.first() && executor.appliesToUser(it.type))
            {
                return@map null
            }

            val optional = it.value != null
            val localeType = if (optional)
            {
                "optional-argument"
            } else
            {
                "required-argument"
            }

            return@map Locale.retrieveLocale()[localeType]!!.replace("{name}", it.name)
        }
        .filterNotNull()
        .joinToString(" ")

    fun handleArguments(
        executor: Executor<*>,
        args: Array<String>
    ): Array<Any?>
    {
        val arguments = mutableListOf<Any?>()
        val skipFirst = executor.appliesToUser(this.arguments[0].type)

        if (skipFirst)
        {
            try
            {
                executor.toUser()?.let {
                    arguments.add(this.arguments[0].type.cast(it))
                }
            } catch (ignored: ClassCastException)
            {
                throw ArgumentParseException(
                    Locale.retrieveLocale(executor)["user-not-found"]!!
                )
            }
        }

        val offset = if (skipFirst)
        {
            1
        } else
        {
            0
        }

        for (i in offset until this.arguments.size)
        {
            val argument = this.arguments[i]

            if (argument.value == null && args.size - 1 < i - offset)
            {
                throw ArgumentCountException(
                    Locale.retrieveLocale(executor)["does-not-meet-arguments"]!!
                        .replace("{label}", this.label)
                        .replace("{arguments}", this.formatArguments(executor))
                )
            }

            val offsetIndex = i - offset

            arguments.add(
                if (args.size - 1 < offsetIndex)
                {
                    argument.value
                } else if (i == this.arguments.size - 1 && args.size > offsetIndex && argument.type == Array<String>::class.java)
                {
                    args.toList().subList(offsetIndex, args.size).toTypedArray()
                } else
                {
                    val exception = ArgumentParseException(
                        Locale.retrieveLocale(executor)["unable-to-parse-argument"]!!
                            .replace("{arg}", args[offsetIndex])
                    )

                    try
                    {
                        argument.convertToValue(args[offsetIndex]) ?: throw exception
                    } catch (ignored: Exception)
                    {
                        throw exception
                    }
                }
            )
        }

        return arguments
            .toTypedArray()
    }
}