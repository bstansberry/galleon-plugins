/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.galleon.plugin.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.core.launcher.CliCommandBuilder;

/**
 * @author Alexey Loubyansky
 *
 */
public class CliScriptRunner {

    public static void runCliScript(Path installHome, Path script, MessageWriter messageWriter) throws ProvisioningException {
        final CliCommandBuilder builder = CliCommandBuilder
                .of(installHome)
                .addCliArgument("--no-operation-validation")
                .addCliArgument("--echo-command")
                .addCliArgument("--file=" + script);
        List<String> arguments = builder.build();
        messageWriter.verbose("Executing jboss console: %s", arguments.stream().collect(Collectors.joining(" ")));
        final ProcessBuilder processBuilder = new ProcessBuilder(arguments).redirectErrorStream(true);
        processBuilder.environment().put("JBOSS_HOME", installHome.toString());

        execute(processBuilder, messageWriter);
    }

    private static void execute(final ProcessBuilder processBuilder, MessageWriter messageWriter) throws ProvisioningException {
        final Process cliProcess;
        try {
            cliProcess = processBuilder.start();

            String config = null;
            final StringWriter errorWriter = new StringWriter();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(cliProcess.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter writer = new BufferedWriter(errorWriter)) {
                String line = reader.readLine();
                boolean flush = false;
                while (line != null) {
                    if (line.equals("}")) {
                        flush = true;
                    } else {
                        if(line.startsWith("&config ")) {
                            config = line;
                        }
                        if (flush) {
                            writer.flush();
                            errorWriter.getBuffer().setLength(0);
                            flush = false;
                        }
                    }
                    writer.write(line);
                    writer.newLine();
                    line = reader.readLine();
                }
            } catch (IOException e) {
                messageWriter.error(e, e.getMessage());
            }

            if (cliProcess.isAlive()) {
                try {
                    cliProcess.waitFor();
                } catch (InterruptedException e) {
                    messageWriter.error(e, e.getMessage());
                }
            }

            if (cliProcess.exitValue() != 0) {
                throw new ProvisioningException("Failed to generate " + config.substring(1),
                        new ProvisioningException(errorWriter.getBuffer().toString()));
            }
        } catch (IOException e) {
            throw new ProvisioningException("CLI process failed", e);
        }
    }
}
