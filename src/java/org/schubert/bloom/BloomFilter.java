/*
 *           DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license document, and changing it is allowed as long
 * as the name is changed. You just DO WHAT THE FUCK YOU WANT TO.
 *                http://www.wtfpl.net/txt/copying/
 */

package org.schubert.bloom;

public interface BloomFilter<T> {

    long getMaxElemNum();
    long getBitSize();
    int getHashNum();
    double getExpectErrorRate();

    long getCurrentElemNum();
    double getCurrentErrorRate();

    void addElem(byte[] key, int offset, int length);
    void addElem(byte[] key);
    void addElem(T elem);

    boolean mayContain(byte[] key, int offset, int length);
    boolean mayContain(byte[] key);
    boolean mayContain(T elem);

    void clear();
    boolean isEmpty();
}