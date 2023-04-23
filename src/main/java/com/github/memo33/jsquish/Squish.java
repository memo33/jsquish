/* -----------------------------------------------------------------------------

    Copyright (c) 2006 Simon Brown                          si@sjbrown.co.uk
    Copyright (c) 2016 memo

    Permission is hereby granted, free of charge, to any person obtaining
    a copy of this software and associated documentation files (the
    "Software"), to deal in the Software without restriction, including
    without limitation the rights to use, copy, modify, merge, publish,
    distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to
    the following conditions:

    The above copyright notice and this permission notice shall be included
    in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
    OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
    MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
    IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
    CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
    TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
    SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

   -------------------------------------------------------------------------- */

package io.github.memo33.jsquish;

public final class Squish {

    public enum CompressionType {

        DXT1(8),
        DXT3(16),
        DXT5(16);

        public final int blockSize;
        public final int blockOffset;

        CompressionType(final int blockSize) {
            this.blockSize = blockSize;
            this.blockOffset = blockSize - 8;
        }
    }

    public enum CompressionMethod {

        CLUSTER_FIT() {

            CompressorColourFit getCompressor(final ColourSet colours, final CompressionType type, final CompressionMetric metric, final ColourBlock writer) {
                return new CompressorCluster(colours, type, metric, writer);

            }},
        RANGE_FIT() {

            CompressorColourFit getCompressor(final ColourSet colours, final CompressionType type, final CompressionMetric metric, final ColourBlock writer) {
                return new CompressorRange(colours, type, metric, writer);

            }};

        abstract CompressorColourFit getCompressor(ColourSet colours, CompressionType type, CompressionMetric metric, ColourBlock writer);

    }

    public enum CompressionMetric {

        PERCEPTUAL(0.2126f, 0.7152f, 0.0722f),
        UNIFORM(1.0f, 1.0f, 1.0f);

        public final float r;

        public final float g;

        public final float b;

        CompressionMetric(final float r, final float g, final float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        public float dot(final float x, final float y, final float z) {
            return r * x + g * y + b * z;
        }

    }

    private static class CompressionTask {

        private final ColourSet colours = new ColourSet();
        private final ColourBlock writer = new ColourBlock();

        private final CompressionType type;
        private final CompressionMethod method;
        private final CompressionMetric metric;
        private final boolean weightAlpha;

        private final CompressorColourFit multiColour;
        private CompressorSingleColour singleColour = null;
        private CompressorAlpha alphaCompressor = null;

        CompressionTask(CompressionType type, CompressionMethod method, CompressionMetric metric, boolean weightAlpha) {
            this.type = type;
            this.method = method;
            this.metric = metric;
            this.weightAlpha = weightAlpha;
            this.multiColour = method.getCompressor(colours, type, metric, writer);
        }

        CompressorSingleColour getSingleColourCompressor() {
            // initialise if needed
            if (singleColour == null) {
                singleColour = new CompressorSingleColour(colours, type, writer);
            }
            return singleColour;
        }

        CompressorAlpha getAlphaCompressor() {
            // initialise if needed
            if (alphaCompressor == null) {
                alphaCompressor = new CompressorAlpha();
            }
            return alphaCompressor;
        }
    }

    private Squish() {
    }

    public static int getStorageRequirements(final int width, final int height, final CompressionType type) {
        if ( width <= 0 || height <= 0 )
            throw new IllegalArgumentException("Invalid image dimensions specified: " + width + " x " + height);

        final int blockcount = ((width + 3) / 4) * ((height + 3) / 4);

        return blockcount * type.blockSize;
    }

    public static byte[] compressImage(final byte[] rgba, final int width, final int height, final byte[] blocks, final CompressionType type) {
        return compressImage(rgba, width, height, blocks, type, CompressionMethod.CLUSTER_FIT, CompressionMetric.PERCEPTUAL, false);
    }

    public static byte[] compressImage(final byte[] rgba, final int width, final int height, final byte[] blocks, final CompressionType type, final CompressionMethod method) {
        return compressImage(rgba, width, height, blocks, type, method, CompressionMetric.PERCEPTUAL, false);
    }

    // TODO: Add interface for ByteBuffers
    // concurrent calls allowed!
    public static byte[] compressImage(final byte[] rgba, final int width, final int height, byte[] blocks,
                                       final CompressionType type, final CompressionMethod method, final CompressionMetric metric, final boolean weightAlpha) {
        blocks = checkCompressInput(rgba, width, height, blocks, type);

        final byte[] sourceRGBA = new byte[16 * 4];

        final CompressionTask task = new CompressionTask(type, method, metric, weightAlpha);

        // loop over blocks
        int targetBlock = 0;
        for ( int y = 0; y < height; y += 4 ) {
            for ( int x = 0; x < width; x += 4 ) {
                // build the 4x4 block of pixels
                int targetPixel = 0;
                int mask = 0;
                for ( int py = 0; py < 4; ++py ) {
                    final int sy = y + py;
                    for ( int px = 0; px < 4; ++px ) {
                        // get the source pixel in the image
                        final int sx = x + px;

                        // enable if we're in the image
                        if ( sx < width && sy < height ) {
                            // copy the rgba value
                            int sourcePixel = 4 * (width * sy + sx);
                            for ( int i = 0; i < 4; ++i )
                                sourceRGBA[targetPixel++] = rgba[sourcePixel++];

                            // enable this pixel
                            mask |= (1 << (4 * py + px));
                        } else {
                            // skip this pixel as its outside the image
                            targetPixel += 4;
                        }
                    }
                }

                // compress it into the output
                compress(sourceRGBA, mask, blocks, targetBlock, task);

                // advance
                targetBlock += type.blockSize;
            }
        }

        return blocks;
    }

    private static byte[] checkCompressInput(final byte[] rgba, final int width, final int height, byte[] blocks, final CompressionType type) {
        final int storageSize = getStorageRequirements(width, height, type);

        if ( rgba == null || rgba.length < (width * height * 4) )
            throw new IllegalArgumentException("Invalid source image data specified.");

        if ( blocks == null || blocks.length < storageSize )
            blocks = new byte[storageSize];

        return blocks;
    }

    private static void compress(final byte[] rgba, final int mask, final byte[] block, final int offset, final CompressionTask task) {
        final CompressionType type = task.type;
        // get the block locations
        final int colourBlock = offset + type.blockOffset;
        final int alphaBlock = offset;

        // create the minimal point set
        task.colours.init(rgba, mask, type, task.weightAlpha);

        // check the compression type and compress colour
        final CompressorColourFit fit;
        if ( task.colours.getCount() == 1 ) // always do a single colour fit
            fit = task.getSingleColourCompressor();
        else
            fit = task.multiColour;
        fit.init();
        fit.compress(block, colourBlock);

        // compress alpha separately if necessary
        if ( type == CompressionType.DXT3 )
            task.getAlphaCompressor().compressAlphaDxt3(rgba, mask, block, alphaBlock);
        else if ( type == CompressionType.DXT5 )
            task.getAlphaCompressor().compressAlphaDxt5(rgba, mask, block, alphaBlock);
    }

    public static byte[] decompressImage(byte[] rgba, final int width, final int height, final byte[] blocks, final CompressionType type) {
        rgba = checkDecompressInput(rgba, width, height, blocks, type);

        final byte[] targetRGBA = new byte[16 * 4];
        final ColourBlock writer = new ColourBlock();
        final CompressorAlpha alphaCompressor = new CompressorAlpha();

        // loop over blocks
        int sourceBlock = 0;
        for ( int y = 0; y < height; y += 4 ) {
            for ( int x = 0; x < width; x += 4 ) {
                // decompress the block
                decompress(targetRGBA, blocks, sourceBlock, type, writer, alphaCompressor);

                // write the decompressed pixels to the correct image locations
                int sourcePixel = 0;
                for ( int py = 0; py < 4; ++py ) {
                    for ( int px = 0; px < 4; ++px ) {
                        // get the target location
                        int sx = x + px;
                        int sy = y + py;
                        if ( sx < width && sy < height ) {
                            // copy the rgba value
                            int targetPixel = 4 * (width * sy + sx);
                            for ( int i = 0; i < 4; ++i )
                                rgba[targetPixel++] = targetRGBA[sourcePixel++];
                        } else {
                            // skip this pixel as its outside the image
                            sourcePixel += 4;
                        }
                    }
                }

                // advance
                sourceBlock += type.blockSize;
            }
        }

        return rgba;
    }

    private static byte[] checkDecompressInput(byte[] rgba, final int width, final int height, final byte[] blocks, final CompressionType type) {
        final int storageSize = getStorageRequirements(width, height, type);

        if ( blocks == null || blocks.length < storageSize )
            throw new IllegalArgumentException("Invalid source image data specified.");

        if ( rgba == null || rgba.length < (width * height * 4) )
            rgba = new byte[(width * height * 4)];

        return rgba;
    }

    private static void decompress(final byte[] rgba, final byte[] block, final int offset, final CompressionType type, final ColourBlock writer, final CompressorAlpha alphaCompressor) {
        // get the block locations
        final int colourBlock = offset + type.blockOffset;
        final int alphaBock = offset;

        // decompress colour
        writer.decompressColour(rgba, block, colourBlock, type == CompressionType.DXT1);

        // decompress alpha separately if necessary
        if ( type == CompressionType.DXT3 )
            alphaCompressor.decompressAlphaDxt3(rgba, block, alphaBock);
        else if ( type == CompressionType.DXT5 )
            alphaCompressor.decompressAlphaDxt5(rgba, block, alphaBock);
    }

}
