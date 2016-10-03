/*
 *           DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license document, and changing it is allowed as long
 * as the name is changed. You just DO WHAT THE FUCK YOU WANT TO.
 *                http://www.wtfpl.net/txt/copying/
 */

package org.schubert.bloom;

import java.nio.charset.Charset;

/**
 * A Bloom filter is a space-efficient probabilistic data structure, conceived by Burton Howard Bloom in 1970,
 * that is used to test whether an element is a member of a set. False positive matches are possible, but false
 * negatives are not; i.e. a query returns either "possibly in set" or "definitely not in set". Elements can
 * be added to the set, but not removed. The more elements that are added to the set, the larger the probability
 * of false positives. <br>
 * <p>
 * Refer to following descriptions:<br>
 *     <a href=http://en.wikipedia.org/wiki/Bloom_filter>Wiki of Bloom Filter</a><br>
 *     <a href=http://billmill.org/bloomfilter-tutorial>Bloom Filter by Example</a><br>
 * </p>
 * This simple implementation provides a fast and evenly distributed Murmur hash function, and support larger
 * bitset (at most 137,438,953,472 bits, i.e. 16GB), and so support larger number of elements.
 */
public class SimpleBloomFilter<T> implements BloomFilter<T> {
    /** Encoding charset used to calculate hash value for string. */
    public static final Charset CHARSET = Charset.forName("UTF-8");

    /** The maximum bit size, must make the index of long typed array valid, since the index must be a integer. */
    public static final long MAX_BIT_SIZE = (long) (Integer.MAX_VALUE) * (long) (Long.SIZE);

    /** The maximum number of hash functions, too many hash functions would make the bloom filter lose efficacy. */
    public static final int MAX_HASH_NUM = 128;

    /* Bit set holder, here use long typed array for at most 16 billion bits (16GB in memory). */
    private long[] bitWords;

    /*
     * The expected maximum number of elements to be added, it is usually a approximate value.
     * i.e. the "n" in Bloom Filter formula
     */
    private long nElemNum;

    /*
     * The expect number of bits, it determine the memory requirement.
     * i.e. the "m" in Bloom Filter formula
     */
    private long mBitSize;

    /* The number of hash functions. ie.e. the "k" in Bloom Filter formula. */
    private int kHashNum;

    /* The expected False Positive Probability. */
    private double errorRate;

    /* Currently number of added elements. */
    private long currentElemNum = 0L;

    /**
     * Construct a empty Bloom Filter by expected total number of elements, size of memory and number of hash.
     * This is not a good way to create a Bloom Filter, because you cannot always guarantee reasonable
     * "False Positive Probability".
     * @param elemNum The expected number of elements to be added into this Bloom Filter
     *                It is usually a approximate value estimated by user.
     * @param bitSize The number bits to hold the Bloom Filter, it determines the memory requirement.
     * @param hashNum The number of hash function to filter a element.
     * @throws IllegalArgumentException when some arguments invalid
     */
    public SimpleBloomFilter(long elemNum, long bitSize, int hashNum)
            throws IllegalArgumentException {

        /* Sanity check of the arguments. */
        if (elemNum <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of elements: " + elemNum);
        }

        if ((bitSize <= 0) || (bitSize > MAX_BIT_SIZE)) {
            throw new IllegalArgumentException("Invalid bit size: " + bitSize
                    + ", should within (0, " + MAX_BIT_SIZE + "]");
        }

        if ((hashNum <= 0) || (hashNum > MAX_HASH_NUM)) {
            throw new IllegalArgumentException("Invalid number of hash function: " + hashNum);
        }

        this.nElemNum  = elemNum;
        this.mBitSize  = bitSize;
        this.kHashNum  = hashNum;
        this.errorRate = SimpleBloomFilter.calcErrorRate(this.nElemNum, this.mBitSize, this.kHashNum);

        initBitWords(this.mBitSize);
    }

    /**
     * Construct a empty Bloom Filter by expected total number of elements, and expected False Positive Probability.
     * If the user know the approximate cardinality of it's data set, and have a expected False Positive Probability,
     * please use this Constructor.
     * @param elemNum   The expected number of elements to be added into this Bloom Filter
     *                  It is usually a approximate value estimated by user.
     * @param errorRate Expected False Positive Probability.
     * @throws IllegalArgumentException when some arguments invalid
     */
    public SimpleBloomFilter(long elemNum, double errorRate)
            throws IllegalArgumentException {
        long tmpBitSize;
        int  tmpHashNum;

        /* Sanity check of the arguments. */
        if (elemNum <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of elements: " + elemNum);
        }

        if ((errorRate < 0.00000000001) || (errorRate > 1.0)) {
            throw new IllegalArgumentException("Invalid false positive probability: " + errorRate);
        }

        this.nElemNum  = elemNum;
        this.errorRate = errorRate;

        /*
         * when given a very little errorRate, may get a very large bitSize, here we avoid it be too large,
         * but it still may larger than the available heap in JVM, and result in OOME.
         */
        tmpBitSize = SimpleBloomFilter.calcBitSize(this.nElemNum, this.errorRate);
        this.mBitSize   = (tmpBitSize <= MAX_BIT_SIZE) ? tmpBitSize : MAX_BIT_SIZE;

        tmpHashNum = SimpleBloomFilter.calcHashNum(this.nElemNum, this.mBitSize);
        this.kHashNum  = (tmpHashNum <= MAX_HASH_NUM) ? tmpHashNum : MAX_HASH_NUM;

        initBitWords(this.mBitSize);
    }

    /**
     * Construct a empty Bloom Filter by expected bit size (memory), and expected False Positive Probability.
     * If the user have strict or explicit memory limitation, and have a expected False Positive Probability,
     * please use this Constructor.
     * @param errorRate Expected False Positive Probability.
     * @param bitSize   The number bits to hold the Bloom Filter, it determines the memory requirement.
     * @throws IllegalArgumentException when some arguments invalid
     */
    public SimpleBloomFilter(double errorRate, long bitSize)
            throws IllegalArgumentException {
        long tmpElemNum;
        int  tmpHashNum;

        /* Sanity check of the arguments. */
        if ((errorRate < 0.00000000001) || (errorRate > 1.0)) {
            throw new IllegalArgumentException("Invalid false positive probability: " + errorRate);
        }

        if ((bitSize <= 0) || (bitSize > MAX_BIT_SIZE)) {
            throw new IllegalArgumentException("Invalid bit size: " + bitSize
                    + ", should within (0, " + MAX_BIT_SIZE + "]");
        }

        this.errorRate = errorRate;
        this.mBitSize  = bitSize;

        /*
         * may calculate to get a zero, which would cause exception in later calculation.
         * here we avoid it be zero.
         */
        tmpElemNum = SimpleBloomFilter.calcElemNum(this.mBitSize, this.errorRate);
        this.nElemNum   = (tmpElemNum > 0) ? tmpElemNum : 1;

        tmpHashNum = SimpleBloomFilter.calcHashNum(this.nElemNum, this.mBitSize);
        this.kHashNum  = (tmpHashNum <= MAX_HASH_NUM) ? tmpHashNum : MAX_HASH_NUM;

        initBitWords(this.mBitSize);
    }

    /**
     * Create and initialize the bitset, represent in long word array.
     * @param bitSize The number bits to hold the Bloom Filter.
     */
    private void initBitWords(long bitSize) {
        bitWords = new long[bitSizeToWordSize(bitSize)];

        /* Clean to make them empty */
        for (int i = 0; i < bitWords.length; i++) {
            bitWords[i] = 0;
        }
    }

    /**
     * Get the expect maximum number of elements.
     * @return the expect maximum number of elements.
     */
    @Override
    public long getMaxElemNum() {
        return nElemNum;
    }

    /**
     * Get the bit size, it represent the memory overhead (bitSize / 8).
     * @return the bit size.
     */
    @Override
    public long getBitSize() {
        return mBitSize;
    }

    /**
     * Get the number of hash function.
     * @return the number of hash function.
     */
    @Override
    public int getHashNum() {
        return kHashNum;
    }

    /**
     * Get the expected error rate, i.e. False Positive Probability
     * @return the expected error rate, i.e. False Positive Probability
     */
    @Override
    public double getExpectErrorRate() {
        return errorRate;
    }

    /**
     * Get the current number of added elements.
     * @return the current number of added elements.
     */
    @Override
    public long getCurrentElemNum() {
        return currentElemNum;
    }

    /**
     * Get the current actual False Positive Probability
     * @return the current actual False Positive Probability
     */
    @Override
    public double getCurrentErrorRate() {
        return SimpleBloomFilter.calcErrorRate(currentElemNum, mBitSize, kHashNum);
    }

    /**
     * Add a element into the Bloom Filter.
     * @param key    the bytes array of element key
     * @param offset the begin offset of the element key
     * @param length the valid length of the element key
     */
    @Override
    public void addElem(byte[] key, int offset, int length) {
        long hashValue;

        for (int i = 0; i < kHashNum; i++) {
            hashValue = getHashValue(key, offset, length, i);
            setBit(Math.abs(hashValue) % mBitSize);
        }

        currentElemNum++;
    }

    /**
     * Add a element into the Bloom Filter.
     * @param key the bytes array of element key
     */
    @Override
    public void addElem(byte[] key) {
        addElem(key, 0, key.length);
    }

    /**
     * Add an object to the Bloom filter. The output from the object's toString() method is used
     * as input to the hash functions.
     * @param elem the element object
     */
    @Override
    public void addElem(T elem) {
        addElem(elem.toString().getBytes(CHARSET));
    }

    /**
     * Return true if the element may have been added into the Bloom Filter.
     * User can use getCurrentErrorRate() to calculate the probability of this being correct.
     * @param key    the bytes array of element key
     * @param offset the begin offset of the element key
     * @param length the valid length of the element key
     * @return true if the element may have been added into the Bloom Filter.
     */
    @Override
    public boolean mayContain(byte[] key, int offset, int length) {
        long hashValue;

        for (int i = 0; i < kHashNum; i++) {
            hashValue = getHashValue(key, offset, length, i);
            if(!isSet(Math.abs(hashValue) % mBitSize)) {
                /* definitely not in set */
                return false;
            }
        }

        /* possibly in set, with False Positive Probability. */
        return true;
    }

    /**
     * Return true if the element may have been added into the Bloom Filter.
     * @param key the bytes array of element key
     * @return true if the element may have been added into the Bloom Filter.
     */
    @Override
    public boolean mayContain(byte[] key) {
        return mayContain(key, 0, key.length);
    }

    /**
     * Return true if the element may have been added into the Bloom Filter.
     * @param elem the element object
     * @return true if the element may have been added into the Bloom Filter.
     */
    @Override
    public boolean mayContain(T elem) {
        return mayContain(elem.toString().getBytes(CHARSET));
    }

    /**
     * Clear the and make the Bloom Filter empty.
     */
    @Override
    public void clear() {
        for (int i = 0; i < bitWords.length; i++) {
            bitWords[i] = 0;
        }
        currentElemNum = 0;
    }

    /**
     * Check whether the Bloom Filter is empty.
     * @return true if it is really empty.
     */
    @Override
    public boolean isEmpty() {
        if (currentElemNum > 0) {
            return false;
        }

        for (long word : bitWords) {
            if (word != 0) {
                return false;
            }
        }

        return true;
    }

    /* Following are formulas for Bloom Filter estimation. */

    /**
     * Calculate the False Positive Probability by specified parameters.
     * @param elemNum The number of elements to be added into this Bloom Filter
     * @param bitSize The number bits to hold the Bloom Filter, it determines the memory requirement.
     * @param hashNum The number of hash function to filter a element.
     * @return the False Positive Probability.
     */
    public static double calcErrorRate(long elemNum, long bitSize, int hashNum) {
        return Math.exp(Math.log(1 - Math.exp(-hashNum * elemNum * 1.0 / bitSize)) * hashNum);
    }

    /**
     * Calculate maximum number of elements by given memory space and False Positive Probability.
     * @param bitSize   The number bits to hold the Bloom Filter, it determines the memory requirement.
     * @param errorRate False Positive Probability.
     * @return the number of elements to be added into this Bloom Filter.
     */
    public static long calcElemNum(long bitSize, double errorRate) {
        return (long) (bitSize * ((Math.log(2) * Math.log(2)) / -Math.log(errorRate)));
    }

    /**
     * Calculate the required bit size (then memory space), by given number of elements and False
     * Positive Probability.
     * @param elemNum   The number of elements to be added into this Bloom Filter.
     * @param errorRate False Positive Probability.
     * @return the number bits to hold the Bloom Filter.
     */
    public static long calcBitSize(long elemNum, double errorRate) {
        return (long) Math.ceil(elemNum * (-Math.log(errorRate) / (Math.log(2) * Math.log(2))));
    }

    /**
     * Calculate the optimal number of independent hash functions.
     * @param elemNum The number of elements to be added into this Bloom Filter.
     * @param bitSize The number bits to hold the Bloom Filter.
     * @return the optimal number of independent hash functions.
     */
    public static int calcHashNum(long elemNum, long bitSize) {
        return (int) Math.ceil(Math.log(2) * (bitSize / elemNum));
    }

    /**
     * Calculate the size of long word array according to bit size.
     * @param bitSize The number bits to hold the Bloom Filter.
     * @return the size of long word array.
     */
    private int bitSizeToWordSize(long bitSize) {
        if ((bitSize % Long.SIZE) != 0) {
            return (int) (bitSize / Long.SIZE) + 1;
        } else {
            return (int) (bitSize / Long.SIZE);
        }
    }

    /**
     * Calculate the index of long word array according to the bit index.
     * @param bitIndex the bit index.
     * @return the index of long word array
     */
    private int bitIndexToWordIndex(long bitIndex) {
        return (int) (bitIndex / Long.SIZE);
    }

    /**
     * Calculate the offset in the long word according to the bit index.
     * @param bitIndex the bit index.
     * @return the offset in the long word.
     */
    private int bitIndexToBitOffset(long bitIndex) {
        return (int) (bitIndex % Long.SIZE);
    }

    /**
     * Set bit in the bitset.
     * @param bitIndex the bit index.
     */
    private void setBit(long bitIndex) {
        bitWords[bitIndexToWordIndex(bitIndex)] |= (1 << bitIndexToBitOffset(bitIndex));
    }

    /**
     * Check whether the bit is set or not.
     * @param bitIndex the bit index.
     * @return true if the bit is set (1).
     */
    private boolean isSet(long bitIndex) {
        return ((bitWords[bitIndexToWordIndex(bitIndex)] & (1 << bitIndexToBitOffset(bitIndex))) != 0);
    }

    /**
     * Murmur Hash Function, it is very fast, non-cryptographic, and even distributed.
     * @param key    the key byte array
     * @param offset the begin offset of the key bytes
     * @param length the length of the valid key bytes
     * @param seed   seed value
     * @return the long hash value
     */
    private long getHashValue(byte[] key, int offset, int length, int seed) {
        int m = 0x5bd1e995;
        int r = 24;
        int h = seed ^ length;
        int len_4 = length >> 2;
        int len_m;
        int left;
        int i_m;

        for (int i = 0; i < len_4; i++) {
            int i_4 = (i << 2) + offset;
            int k = key[i_4 + 3];
            k = k << 8;
            k = k | (key[i_4 + 2] & 0xff);
            k = k << 8;
            k = k | (key[i_4 + 1] & 0xff);
            k = k << 8;
            k = k | (key[i_4] & 0xff);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        /* avoid calculating modulo */
        len_m = len_4 << 2;
        left = length - len_m;
        i_m = len_m + offset;

        if (left != 0) {
            if (left >= 3) {
                h ^= key[i_m + 2] << 16;
            }
            if (left >= 2) {
                h ^= key[i_m + 1] << 8;
            }
            if (left >= 1) {
                h ^= key[i_m];
            }
            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        /* just a simple way to get a long hash value */
        return ((long) h << 32) | (long) h;
    }
}
