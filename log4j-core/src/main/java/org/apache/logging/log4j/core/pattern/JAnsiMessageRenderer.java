/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.pattern;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.status.StatusLogger;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiRenderer;
import org.fusesource.jansi.AnsiRenderer.Code;

/**
 * Renders an input as ANSI escaped output.
 * 
 * Uses the JAnsi rendering syntax as the default to render a message into an ANSI escaped string.
 * 
 * The default syntax for embedded ANSI codes is:
 *
 * <pre>
 *   &#64;|<em>code</em>(,<em>code</em>)* <em>text</em>|@
 * </pre>
 *
 * For example, to render the message {@code "Hello"} in green, use:
 *
 * <pre>
 *   &#64;|green Hello|@
 * </pre>
 *
 * To render the message {@code "Hello"} in bold and red, use:
 *
 * <pre>
 *   &#64;|bold,red Warning!|@
 * </pre>
 * 
 * You can also define custom style names in the configuration with the syntax:
 * 
 * <pre>
 * %message{ansi}{StyleName=value(,value)*( StyleName=value(,value)*)*}%n
 * </pre>
 * 
 * For example:
 * 
 * <pre>
 * %message{ansi}{WarningStyle=red,bold KeyStyle=white ValueStyle=blue}%n
 * </pre>
 * 
 * The call site can look like this:
 * 
 * <pre>
 * logger.info("@|KeyStyle {}|@ = @|ValueStyle {}|@", entry.getKey(), entry.getValue());
 * </pre>
 * 
 * Note: This class originally copied and then heavily modified code from JAnsi's AnsiRenderer (which is licensed as
 * Apache 2.0.)
 * 
 * @see AnsiRenderer
 */
public final class JAnsiMessageRenderer implements MessageRenderer {

    private final int beginTokenLen;
    private final int endTokenLen;
    private final String beginToken;
    private final String endToken;
    private final Map<String, Code[]> styleMap;

    public JAnsiMessageRenderer(final String[] formats) {
        String tempBeginToken = AnsiRenderer.BEGIN_TOKEN;
        String tempEndToken = AnsiRenderer.END_TOKEN;
        Map<String, Code[]> map;
        if (formats.length > 1) {
            final String allStylesStr = formats[1];
            // Style def split
            final String[] allStyleAssignmentsArr = allStylesStr.split(" ");
            map = new HashMap<>(allStyleAssignmentsArr.length);
            for (final String styleAssignmentStr : allStyleAssignmentsArr) {
                final String[] styleAssignmentArr = styleAssignmentStr.split("=");
                if (styleAssignmentArr.length != 2) {
                    StatusLogger.getLogger().warn("{} parsing style \"{}\", expected format: StyleName=Code(,Code)*",
                            getClass().getSimpleName(), styleAssignmentStr);
                } else {
                    final String styleName = styleAssignmentArr[0];
                    final String codeListStr = styleAssignmentArr[1];
                    final String[] codeNames = codeListStr.split(",");
                    if (codeNames.length == 0) {
                        StatusLogger.getLogger().warn(
                                "{} parsing style \"{}\", expected format: StyleName=Code(,Code)*",
                                getClass().getSimpleName(), styleAssignmentStr);
                    } else {
                        switch (styleName) {
                        case "BeginToken":
                            tempBeginToken = codeNames[0];
                            break;
                        case "EndToken":
                            tempEndToken = codeNames[0];
                            break;
                        default:
                            final Code[] codes = new Code[codeNames.length];
                            for (int i = 0; i < codes.length; i++) {
                                codes[i] = toCode(codeNames[i]);
                            }
                            map.put(styleName, codes);
                        }
                    }
                }
            }
        } else {
            map = new HashMap<>(0);
        }
        styleMap = map;
        beginToken = tempBeginToken;
        endToken = tempEndToken;
        beginTokenLen = tempBeginToken.length();
        endTokenLen = tempEndToken.length();
    }

    private void render(final Ansi ansi, final Code code) {
        if (code.isColor()) {
            if (code.isBackground()) {
                ansi.bg(code.getColor());
            } else {
                ansi.fg(code.getColor());
            }
        } else if (code.isAttribute()) {
            ansi.a(code.getAttribute());
        }
    }

    private void render(final Ansi ansi, final Code... codes) {
        for (final Code code : codes) {
            render(ansi, code);
        }
    }

    private String render(final String text, final String... names) {
        final Ansi ansi = Ansi.ansi();
        for (final String name : names) {
            final Code[] codes = styleMap.get(name);
            if (codes != null) {
                render(ansi, codes);
            } else {
                render(ansi, toCode(name));
            }
        }
        return ansi.a(text).reset().toString();
    }

    @Override
    public void render(final StringBuilder input, final StringBuilder output) throws IllegalArgumentException {
        int i = 0;
        int j, k;

        while (true) {
            j = input.indexOf(beginToken, i);
            if (j == -1) {
                if (i == 0) {
                    output.append(input);
                    return;
                }
                output.append(input.substring(i, input.length()));
                return;
            }
            output.append(input.substring(i, j));
            k = input.indexOf(endToken, j);

            if (k == -1) {
                output.append(input);
                return;
            }
            j += beginTokenLen;
            final String spec = input.substring(j, k);

            final String[] items = spec.split(AnsiRenderer.CODE_TEXT_SEPARATOR, 2);
            if (items.length == 1) {
                output.append(input);
                return;
            }
            final String replacement = render(items[1], items[0].split(","));

            output.append(replacement);

            i = k + endTokenLen;
        }
    }

    private Code toCode(final String name) {
        return Code.valueOf(name.toUpperCase(Locale.ENGLISH));
    }

}
