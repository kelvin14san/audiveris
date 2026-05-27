//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                  R u n H e a d J s o n E x p o r t                             //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//
//  Copyright © Audiveris 2026. All rights reserved.
//
//  This program is free software: you can redistribute it and/or modify it under the terms of the
//  GNU Affero General Public License as published by the Free Software Foundation, either version
//  3 of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//  See the GNU Affero General Public License for more details.
//
//  You should have received a copy of the GNU Affero General Public License along with this
//  program.  If not, see <http://www.gnu.org/licenses/>.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package org.audiveris.omr;

import org.audiveris.omr.sheet.Book;
import org.audiveris.omr.sheet.BookManager;
import org.audiveris.omr.sheet.Sheet;
import org.audiveris.omr.sheet.SheetStub;
import org.audiveris.omr.sheet.SystemInfo;
import org.audiveris.omr.sig.inter.HeadInter;
import org.audiveris.omr.sig.inter.Inter;
import org.audiveris.omr.step.OmrStep;
import org.audiveris.omr.step.RunClass;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;

/**
 * Export note heads from Audiveris internal SIG model into JSONL.
 * <p>
 * Usage example (batch mode):
 * <pre>
 * audiveris -batch -transcribe -run org.audiveris.omr.RunHeadJsonExport input.pdf
 * </pre>
 * Output file:
 * <pre>
 * &lt;output-folder&gt;/&lt;book-radix&gt;-heads.jsonl
 * </pre>
 */
public class RunHeadJsonExport
        extends RunClass
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final Logger logger = LoggerFactory.getLogger(RunHeadJsonExport.class);

    //~ Constructors -------------------------------------------------------------------------------

    public RunHeadJsonExport (Book book,
                              SortedSet<Integer> sheetIds)
    {
        super(book, sheetIds);
    }

    //~ Methods ------------------------------------------------------------------------------------

    @Override
    public void process ()
    {
        final Path outputFolder = BookManager.getDefaultBookFolder(book);

        if (outputFolder == null) {
            logger.warn("No output folder available for {}", book);

            return;
        }

        final Path outPath = outputFolder.resolve(book.getRadix() + "-heads.jsonl");
        int count = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(
                outPath,
                StandardCharsets.UTF_8)) {
            for (SheetStub stub : book.getValidSelectedStubs()) {
                if ((sheetIds != null) && !sheetIds.contains(stub.getNumber())) {
                    continue;
                }

                if (!stub.isDone(OmrStep.RHYTHMS)) {
                    logger.info("Skipping sheet {} (RHYTHMS not done)", stub.getNumber());
                    continue;
                }

                final Sheet sheet = stub.getSheet();
                final int page = stub.getNumber();

                for (SystemInfo system : sheet.getSystems()) {
                    for (Inter inter : system.getSig().inters(HeadInter.class)) {
                        final HeadInter head = (HeadInter) inter;
                        try {
                            writer.write(toJsonLine(page, head));
                            writer.newLine();
                            count++;
                        } catch (Exception ex) {
                            logger.warn("Failed to export one head on page {}: {}", page, ex.toString());
                        }
                    }
                }
            }
        } catch (IOException ex) {
            logger.warn("Could not write {}", outPath, ex);
            return;
        }

        logger.info("Exported {} note heads to {}", count, outPath.toAbsolutePath());
    }

    private String toJsonLine (int page,
                               HeadInter head)
    {
        final Point center = head.getCenter();

        final Integer x = (center != null) ? center.x : null;
        final Integer y = (center != null) ? center.y : null;

        String step = null;
        Integer octave = null;
        Integer alteration = null;
        String label = null;

        try {
            if (head.getStep() != null) {
                step = head.getStep().name();
                label = fixedDoLabel(step);
            }
        } catch (Exception ignored) {
            // keep null
        }

        try {
            octave = head.getOctave();
        } catch (Exception ignored) {
            // keep null
        }

        try {
            alteration = head.getAlteration(null);
        } catch (Exception ignored) {
            // keep null
        }

        return "{"
                + "\"page\":" + page
                + ",\"x\":" + intOrNull(x)
                + ",\"y\":" + intOrNull(y)
                + ",\"step\":" + quoteOrNull(step)
                + ",\"octave\":" + intOrNull(octave)
                + ",\"alteration\":" + intOrNull(alteration)
                + ",\"label\":" + quoteOrNull(label)
                + "}";
    }

    private static String fixedDoLabel (String step)
    {
        return switch (step) {
            case "C" -> "ド";
            case "D" -> "レ";
            case "E" -> "ミ";
            case "F" -> "ファ";
            case "G" -> "ソ";
            case "A" -> "ラ";
            case "B" -> "シ";
            default -> null;
        };
    }

    private static String intOrNull (Integer value)
    {
        return (value == null) ? "null" : Integer.toString(value);
    }

    private static String quoteOrNull (String value)
    {
        return (value == null) ? "null" : ("\"" + value + "\"");
    }
}
