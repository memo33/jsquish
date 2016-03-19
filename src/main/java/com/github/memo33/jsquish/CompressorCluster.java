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

package com.github.memo33.jsquish;

import com.github.memo33.jsquish.Squish.CompressionType;
import com.github.memo33.jsquish.Squish.CompressionMetric;

/* Compared to the original code, we improve the performance by iterating
 * only over clusters that are similar to a canonical cluster choice,
 * which saves about 50% of total iterations (number of least squares
 * problems). This makes subiterations shorter, but on average a bit deeper,
 * so we increase MAX_ITERATIONS a bit (this bound is rarely reached anyway).
 *
 * As the number of iterations is reduced, the error might increase
 * slightly (usually less than 0.1%) by running into a different local
 * minimum.
 */
final class CompressorCluster extends CompressorColourFit {

    private static final int MAX_ITERATIONS = 10;

    private final int[] indices = new int[16];
    private final int[] bestIndices = new int[16];
    private final int[] unordered = new int[16];
    private final int[] orders = new int[16 * MAX_ITERATIONS];

    private final float[] alpha = new float[16];
    private final float[] beta = new float[16];
    private final float[] weights = new float[16];
    private final float[] weighted = new float[16 * 3];

    private final CompressionMetric metric;
    private final ColourBlock colourBlockWriter;
    private final Vec xxSum = new Vec();

    private Vec principle;
    private float totalBestError;

    CompressorCluster(final ColourSet colours, final CompressionType type, final CompressionMetric metric, final ColourBlock writer) {
        super(colours, type);
        // initialise the metric
        this.metric = metric;

        this.colourBlockWriter = writer;
    }

    void init() {
        // initialise the best error
        totalBestError = Float.MAX_VALUE;

        // get the covariance matrix
        final Matrix covariance = Matrix.computeWeightedCovariance(colours, null);

        // compute the principle component
        principle = Matrix.computePrincipleComponent(covariance);
    }

    void compress3(final byte[] block, final int offset) {
        final int count = colours.getCount();

        final Vec bestStart = new Vec(0.0f);
        final Vec bestEnd = new Vec(0.0f);
        float bestError = this.totalBestError;

        final Vec a = new Vec();
        final Vec b = new Vec();

        // prepare an ordering using the principle axis
        int[] canonical = constructOrderingAndCanonicalCluster(principle, 0, false);

        // check all possible clusters and iterate on the total order
        // (instead of checking all clusters, we only check those that
        // are similar to the canonical one - saves about >50% of iterations)
        int bestIteration = 0;
        for ( int iteration = 0; ; ) {
            // first cluster [0,i) is at the start
            for ( int m = 0; m < count; ++m ) {
                indices[m] = 0;
                alpha[m] = weights[m];
                beta[m] = 0.0f;
            }
            for (int x = -canonical[1]; x <= canonical[0]; x++) {
                int i = canonical[0] - x;
                // second cluster [i,j) is half along
                for ( int m = i; m < count; ++m ) {
                    indices[m] = 2;
                    alpha[m] = beta[m] = 0.5f * weights[m];
                }
                for (int y = -canonical[2]; y <= canonical[1] && y <= canonical[1]+x; y++) {
                    int j = i + canonical[1] + x - y;
                    // last cluster [j,k) is at the end
                    if ( j < count ) {
                        indices[j] = 1;
                        alpha[j] = 0.0f;
                        beta[j] = weights[j];
                    }

                    // solve a least squares problem to place the endpoints
                    final float error = solveLeastSquares(a, b);

                    // keep the solution if it wins
                    if ( error < bestError ) {
                        bestStart.set(a);
                        bestEnd.set(b);
                        System.arraycopy(indices, 0, bestIndices, 0, 16);
                        bestError = error;
                        bestIteration = iteration;
                    }
                }
            }

            // stop if we didn't improve in this iteration
            if ( bestIteration != iteration )
                break;

            // advance if possible
            if ( ++iteration == MAX_ITERATIONS )
                break;

            // stop if a new iteration is an ordering that has already been tried
            canonical = constructOrderingAndCanonicalCluster(a.set(bestEnd).sub(bestStart), iteration, false);
            if (canonical == null)
                break;
        }

        // save the block if necessary
        if ( bestError < this.totalBestError ) {
            // remap the indices
            final int order = 16 * bestIteration;

            for ( int i = 0; i < count; ++i )
                unordered[orders[order + i]] = bestIndices[i];
            colours.remapIndices(unordered, bestIndices);

            // save the block
            colourBlockWriter.writeColourBlock3(bestStart, bestEnd, bestIndices, block, offset);

            // save the error
            this.totalBestError = bestError;

        }
    }

    void compress4(final byte[] block, final int offset) {
        final int count = colours.getCount();

        final Vec bestStart = new Vec(0.0f);
        final Vec bestEnd = new Vec(0.0f);
        float bestError = this.totalBestError;

        final Vec start = new Vec();
        final Vec end = new Vec();

        // prepare an ordering using the principle axis
        int[] canonical = constructOrderingAndCanonicalCluster(principle, 0, true);

        // check all possible clusters and iterate on the total order
        // (instead of checking all clusters, we only check those that
        // are similar to the canonical one - saves about >50% of iterations)
        int bestIteration = 0;
        for ( int iteration = 0; ; ) {
            // first cluster [0,i) is at the start
            for ( int m = 0; m < count; ++m ) {
                indices[m] = 0;
                alpha[m] = weights[m];
                beta[m] = 0.0f;
            }
            for (int x = -canonical[1]; x <= canonical[0]; x++) {
                int i = canonical[0] - x;
                // second cluster [i,j) is one third along
                for ( int m = i; m < count; ++m ) {
                    indices[m] = 2;
                    alpha[m] = (2.0f / 3.0f) * weights[m];
                    beta[m] = (1.0f / 3.0f) * weights[m];
                }
                for (int y = -canonical[2]; y <= canonical[1] && y <= canonical[1]+x; y++) {
                    int j = i + canonical[1] + x - y;
                    // third cluster [j,k) is two thirds along
                    for ( int m = j; m < count; ++m ) {
                        indices[m] = 3;
                        alpha[m] = (1.0f / 3.0f) * weights[m];
                        beta[m] = (2.0f / 3.0f) * weights[m];
                    }
                    for (int z = -canonical[3]; z <= canonical[2] && z <= canonical[2]+y; z++) {
                        int k = j + canonical[2] + y - z;
                        // last cluster [k,n) is at the end
                        if ( k < count ) {
                            indices[k] = 1;
                            alpha[k] = 0.0f;
                            beta[k] = weights[k];
                        }

                        // solve a least squares problem to place the endpoints
                        final float error = solveLeastSquares(start, end);

                        // keep the solution if it wins
                        if ( error < bestError ) {
                            bestStart.set(start);
                            bestEnd.set(end);
                            System.arraycopy(indices, 0, bestIndices, 0, 16);
                            bestError = error;
                            bestIteration = iteration;
                        }
                    }
                }
            }

            // stop if we didn't improve in this iteration
            if ( bestIteration != iteration )
                break;

            // advance if possible
            ++iteration;
            if ( iteration == MAX_ITERATIONS )
                break;

            // stop if a new iteration is an ordering that has already been tried
            canonical = constructOrderingAndCanonicalCluster(start.set(bestEnd).sub(bestStart), iteration, true);
            if (canonical == null)
                break;
        }

        // save the block if necessary
        if ( bestError < this.totalBestError ) {
            // remap the indices
            final int order = 16 * bestIteration;
            for ( int i = 0; i < count; ++i )
                unordered[orders[order + i]] = bestIndices[i];
            colours.remapIndices(unordered, bestIndices);

            // save the block
            colourBlockWriter.writeColourBlock4(bestStart, bestEnd, bestIndices, block, offset);

            // save the error
            this.totalBestError = bestError;

        }
    }

    private int[] constructOrderingAndCanonicalCluster(final Vec axis, final int iteration, boolean isComp4) {
        // cache some values
        final int count = colours.getCount();
        final Vec[] values = colours.getPoints();

        // build the list of dot products
        final float[] dps = new float[16];
        final int order = 16 * iteration;
        for ( int i = 0; i < count; ++i ) {
            dps[i] = values[i].dot(axis);
            orders[order + i] = i;
        }

        // stable sort using them
        for ( int i = 0; i < count; ++i ) {
            for ( int j = i; j > 0 && dps[j] < dps[j - 1]; --j ) {
                final float tmpF = dps[j];
                dps[j] = dps[j - 1];
                dps[j - 1] = tmpF;

                final int tmpI = orders[order + j];
                orders[order + j] = orders[order + j - 1];
                orders[order + j - 1] = tmpI;
            }
        }

        // check this ordering is unique
        for ( int it = 0; it < iteration; ++it ) {
            final int prev = 16 * it;
            boolean same = true;
            for ( int i = 0; i < count; ++i ) {
                if ( orders[order + i] != orders[prev + i] ) {
                    same = false;
                    break;
                }
            }
            if ( same )
                return null;
        }

        // copy the ordering and weight all the points
        final Vec[] points = colours.getPoints();
        final float[] cWeights = colours.getWeights();
        xxSum.set(0.0f);

        for ( int i = 0, j = 0; i < count; ++i, j += 3 ) {
            final int p = orders[order + i];

            final float weight = cWeights[p];
            final Vec point = points[p];

            weights[i] = weight;

            final float wX = weight * point.x();
            final float wY = weight * point.y();
            final float wZ = weight * point.z();

            xxSum.add(wX * wX, wY * wY, wZ * wZ);

            weighted[j + 0] = wX;
            weighted[j + 1] = wY;
            weighted[j + 2] = wZ;
        }
        return canonicalCluster(dps, count, isComp4);
    }

    private static int[] canonicalCluster(float[] dps, int count, boolean isComp4) {
        final int[] cluster = new int[isComp4 ? 4 : 3];
        if (count == 0) return cluster;
        // comp3:                           comp4:
        // |...o...|...*...|...o...|        |...o...|...*...|...*...|...o...|
        // a   0      1/2      1   b        a   0      1/3     2/3      1   b
        final float a = dps[0];
        final float b = dps[count - 1];
        final float[] c = isComp4
            ? new float[] { (3 * a + b) / 4, (a + b) / 2, (a + 3 * b) / 4, b }
            : new float[] { (2 * a + b) / 3, (a + 2 * b) / 3, b };
        for (int i = 0, j = 0; i < count; i++) {
            while (dps[i] > c[j])
                j++;
            cluster[j] = cluster[j] + 1;
        }
        return cluster;
    }

    private float solveLeastSquares(final Vec start, final Vec end) {
        final int count = colours.getCount();

        float alpha2_sum = 0.0f;
        float beta2_sum = 0.0f;
        float alphabeta_sum = 0.0f;

        float alphax_sumX = 0f;
        float alphax_sumY = 0f;
        float alphax_sumZ = 0f;

        float betax_sumX = 0f;
        float betax_sumY = 0f;
        float betax_sumZ = 0f;

        // accumulate all the quantities we need
        for ( int i = 0, j = 0; i < count; ++i, j += 3 ) {
            final float a = alpha[i];
            final float b = beta[i];

            alpha2_sum += a * a;
            beta2_sum += b * b;
            alphabeta_sum += a * b;

            alphax_sumX += weighted[j + 0] * a;
            alphax_sumY += weighted[j + 1] * a;
            alphax_sumZ += weighted[j + 2] * a;

            betax_sumX += weighted[j + 0] * b;
            betax_sumY += weighted[j + 1] * b;
            betax_sumZ += weighted[j + 2] * b;
        }

        float aX, aY, aZ;
        float bX, bY, bZ;

        // zero where non-determinate
        if ( beta2_sum == 0.0f ) {
            final float rcp = 1.0f / alpha2_sum;

            aX = alphax_sumX * rcp;
            aY = alphax_sumY * rcp;
            aZ = alphax_sumZ * rcp;
            bX = bY = bZ = 0.0f;
        } else if ( alpha2_sum == 0.0f ) {
            final float rcp = 1.0f / beta2_sum;

            aX = aY = aZ = 0.0f;
            bX = betax_sumX * rcp;
            bY = betax_sumY * rcp;
            bZ = betax_sumZ * rcp;
        } else {
            final float rcp = 1.0f / (alpha2_sum * beta2_sum - alphabeta_sum * alphabeta_sum);
            if ( rcp == (1.0f / 0.0f) ) // Detect Infinity
                return Float.MAX_VALUE;

            aX = (alphax_sumX * beta2_sum - betax_sumX * alphabeta_sum) * rcp;
            aY = (alphax_sumY * beta2_sum - betax_sumY * alphabeta_sum) * rcp;
            aZ = (alphax_sumZ * beta2_sum - betax_sumZ * alphabeta_sum) * rcp;

            bX = (betax_sumX * alpha2_sum - alphax_sumX * alphabeta_sum) * rcp;
            bY = (betax_sumY * alpha2_sum - alphax_sumY * alphabeta_sum) * rcp;
            bZ = (betax_sumZ * alpha2_sum - alphax_sumZ * alphabeta_sum) * rcp;
        }

        // clamp the output to [0, 1]
        // clamp to the grid
        aX = clamp(aX, GRID_X, GRID_X_RCP);
        aY = clamp(aY, GRID_Y, GRID_Y_RCP);
        aZ = clamp(aZ, GRID_Z, GRID_Z_RCP);

        start.set(aX, aY, aZ);

        bX = clamp(bX, GRID_X, GRID_X_RCP);
        bY = clamp(bY, GRID_Y, GRID_Y_RCP);
        bZ = clamp(bZ, GRID_Z, GRID_Z_RCP);

        end.set(bX, bY, bZ);

        // compute the error
        final float eX = aX * aX * alpha2_sum + bX * bX * beta2_sum + xxSum.x() + 2.0f * (aX * bX * alphabeta_sum - aX * alphax_sumX - bX * betax_sumX);
        final float eY = aY * aY * alpha2_sum + bY * bY * beta2_sum + xxSum.y() + 2.0f * (aY * bY * alphabeta_sum - aY * alphax_sumY - bY * betax_sumY);
        final float eZ = aZ * aZ * alpha2_sum + bZ * bZ * beta2_sum + xxSum.z() + 2.0f * (aZ * bZ * alphabeta_sum - aZ * alphax_sumZ - bZ * betax_sumZ);

        // apply the metric to the error term
        return metric.dot(eX, eY, eZ);
    }

}
