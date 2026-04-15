/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.codec.derivedsource;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.search.DocIdSetIterator;
import org.opensearch.knn.index.vectorvalues.KNNVectorValues;
import org.opensearch.knn.index.vectorvalues.KNNVectorValuesFactory;

import java.io.IOException;

public class NestedPerFieldDerivedVectorTransformer extends AbstractPerFieldDerivedVectorTransformer {

    private final FieldInfo childFieldInfo;
    private final DerivedSourceReaders derivedSourceReaders;
    private KNNVectorValues<?> vectorValues;
    private int docId = -1;

    /**
     *
     * @param childFieldInfo FieldInfo of the child field
     * @param derivedSourceReaders Readers for access segment info
     */
    public NestedPerFieldDerivedVectorTransformer(FieldInfo childFieldInfo, DerivedSourceReaders derivedSourceReaders) {
        this.childFieldInfo = childFieldInfo;
        this.derivedSourceReaders = derivedSourceReaders;
    }

    @Override
    public Object apply(Object object) {
        if (object == null) {
            return object;
        } else if (docId == DocIdSetIterator.NO_MORE_DOCS) {
            return null;
        }

        try {
            // Get the vector at the current position
            Object rawVector = vectorValues.getVector();
            if (rawVector == null) {
                // No vector at this position, return null
                return null;
            }

            // Format the vector (handles byte[] deserialization if needed)
            Object vector = formatVector(childFieldInfo, () -> rawVector, vectorValues::conditionalCloneVector);

            // After getting the vector, advance to the next one for the next apply() call
            docId = vectorValues.nextDoc();

            return vector;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setCurrentDoc(int offset, int docId) throws IOException {
        vectorValues = KNNVectorValuesFactory.getVectorValues(
            childFieldInfo,
            derivedSourceReaders.getDocValuesProducer(),
            derivedSourceReaders.getKnnVectorsReader()
        );
        // Advance to the offset position (first child of parent doc)
        // This positions the iterator at the first vector we want to read
        this.docId = vectorValues.advance(offset);
    }
}
