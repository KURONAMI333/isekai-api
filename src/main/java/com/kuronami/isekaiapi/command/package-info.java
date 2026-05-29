/**
 * {@code /isekai} operator command. The root literal is built by
 * {@link com.kuronami.isekaiapi.command.IsekaiCommand}; each subcommand
 * ({@code version}, {@code stats}, {@code reload}, {@code query}, {@code validate},
 * {@code preview}, {@code dump}) lives in its own class under {@code command.sub}.
 *
 * <p>All subcommands inherit the permission gate {@code requires(src.hasPermission(2))}
 * from the root literal — none of them are usable by non-operators.
 */
package com.kuronami.isekaiapi.command;
