/*
 *           DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license document, and changing it is allowed as long
 * as the name is changed. You just DO WHAT THE FUCK YOU WANT TO.
 *                http://www.wtfpl.net/txt/copying/
 */

package org.schubert.bloom;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Unit Test for SimpleBloomFilter.
 * Here use JUnit4 as the unit-test framework.
 */
public class SimpleBloomFilterTest {
    private static Random rand = new Random();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testConstructorNMK() throws Exception {
        System.out.println("testConstructorNMK and invalidN");

        long elemNum;
        long bitSize;
        int hashNum;
        double errorRate;
        BloomFilter bloom;

        /* Test normal NMK */
        for (int i = 0; i < 10; i++) {
            elemNum = Math.abs(rand.nextLong() % 10000) + 1;
            bitSize = Math.abs(rand.nextLong() % 100000) + 1;
            hashNum = rand.nextInt(16) + 1;
            bloom = new SimpleBloomFilter(elemNum, bitSize, hashNum);
            assertEquals(elemNum, bloom.getMaxElemNum());
            assertEquals(bitSize, bloom.getBitSize());
            assertEquals(hashNum, bloom.getHashNum());
            assertEquals(0, bloom.getCurrentElemNum());
            errorRate = bloom.getExpectErrorRate();
            assertTrue((errorRate > 0.0) && (errorRate <= 1.0));
        }

        /* Test invalid N */
        elemNum = -1;
        bitSize = 12345;
        hashNum = 7;
        thrown.expect(IllegalArgumentException.class);
        new SimpleBloomFilter(elemNum, bitSize, hashNum);
    }

    @Test
    public void testConstructorNMKinvalidM() throws Exception {
        System.out.println("testConstructorNMKinvalidM");
        thrown.expect(IllegalArgumentException.class);
        new SimpleBloomFilter(12345, SimpleBloomFilter.MAX_BIT_SIZE + 1, 7);
    }

    @Test
    public void testConstructorNMKinvalidK() throws Exception {
        System.out.println("testConstructorNMKinvalidK");
        thrown.expect(IllegalArgumentException.class);
        new SimpleBloomFilter(1000, 10000, SimpleBloomFilter.MAX_HASH_NUM + 1);
    }

    @Test
    public void testConstructorNF() throws Exception {
        System.out.println("testConstructorNF and invalidN");

        long elemNum;
        double errorRate;
        BloomFilter bloom;

        for (int i = 0; i < 100; i++) {
            elemNum = Math.abs(rand.nextLong() % 10000) + 1;
            errorRate = 0.00001 + rand.nextInt(100) / (100 + rand.nextInt(10000));
            bloom = new SimpleBloomFilter(elemNum, errorRate);
            assertEquals(elemNum, bloom.getMaxElemNum());
            assertTrue((bloom.getBitSize() > 0) && (bloom.getBitSize() <= SimpleBloomFilter.MAX_BIT_SIZE));
            assertTrue((bloom.getHashNum() > 0) && (bloom.getHashNum() <= SimpleBloomFilter.MAX_HASH_NUM));
            assertEquals(0, bloom.getCurrentElemNum());
            assertEquals(errorRate, bloom.getExpectErrorRate(), 0.0001);
        }

        /* Test invalid N */
        elemNum = 0;
        errorRate = 0.01;
        thrown.expect(IllegalArgumentException.class);
        new SimpleBloomFilter(elemNum, errorRate);
    }

    @Test
    public void testConstructorNFinvalidF() throws Exception {
        System.out.println("testConstructorNFinvalidF");
        thrown.expect(IllegalArgumentException.class);
        new SimpleBloomFilter(10000, 1.0001);
    }

    @Test
    public void testConstructorFM() throws Exception {
        System.out.println("testConstructorFM and invalidF");

        double errorRate;
        long bitSize;
        BloomFilter bloom;

        for (int i = 0; i < 100; i++) {
            bitSize = Math.abs(rand.nextLong() % 100000) + 1;
            errorRate = 0.00001 + rand.nextInt(100) / (100 + rand.nextInt(10000));
            bloom = new SimpleBloomFilter(errorRate, bitSize);
            assertEquals(bitSize, bloom.getBitSize());
            assertTrue(bloom.getMaxElemNum() > 0);
            assertTrue((bloom.getHashNum() > 0) && (bloom.getHashNum() <= SimpleBloomFilter.MAX_HASH_NUM));
            assertEquals(bloom.getCurrentElemNum(), 0);
            assertEquals(errorRate, bloom.getExpectErrorRate(), 0.0001);
        }

        errorRate = 1.0001;
        bitSize   = 12345;
        thrown.expect(IllegalArgumentException.class);
        new SimpleBloomFilter(errorRate, bitSize);
    }

    @Test
    public void testConstructorFMinvalidM() throws Exception {
        System.out.println("testConstructorFMinvalidM");
        thrown.expect(IllegalArgumentException.class);
        new SimpleBloomFilter(0.0023, SimpleBloomFilter.MAX_BIT_SIZE + 1);
    }

    @Test
    public void testGetMaxElemNum() throws Exception {
        System.out.println("testGetMaxElemNum");
        BloomFilter bloom = new SimpleBloomFilter(0.021, 256789L);
        assertEquals(31935L, bloom.getMaxElemNum());
    }

    @Test
    public void testGetBitSize() throws Exception {
        System.out.println("testGetBitSize");
        BloomFilter bloom = new SimpleBloomFilter(31935L, 0.021);
        assertEquals(256784L, bloom.getBitSize());
    }

    @Test
    public void testGetHashNum() throws Exception {
        System.out.println("testGetHashNum");
        BloomFilter bloom = new SimpleBloomFilter(0.021, 256784L);
        assertEquals(6, bloom.getHashNum());
        bloom = new SimpleBloomFilter(31935L, 0.021);
        assertEquals(6, bloom.getHashNum());
    }

    @Test
    public void testGetExpectErrorRate() throws Exception {
        System.out.println("testGetExpectErrorRate");
        BloomFilter bloom = new SimpleBloomFilter(31935L, 256784L, 6);
        assertEquals(0.021, bloom.getExpectErrorRate(), 0.001);
    }

    @Test
    public void testGetCurrentElemNum() throws Exception {
        System.out.println("testGetCurrentElemNum");
        long availableElems = 1 + rand.nextInt(31935);
        long added;
        BloomFilter<Long> bloom = new SimpleBloomFilter<Long>(31935L, 256784L, 6);
        for (added = 0; added < availableElems; added++) {
            bloom.addElem(added);
        }
        assertEquals(added, bloom.getCurrentElemNum());
    }

    @Test
    public void testGetCurrentErrorRate() throws Exception {
        System.out.println("testGetCurrentErrorRate");
        BloomFilter<Long> bloom = new SimpleBloomFilter<Long>(31935L, 256784L, 6);
        long availableElems = 10000L;
        long added;
        for (added = 0; added < availableElems; added++) {
            bloom.addElem(added);
        }
        int hashNum = (int) Math.ceil(Math.log(2) * (256784L / 31935L));
        double expectErrorRate = Math.exp(Math.log(1 - Math.exp(-hashNum * added * 1.0 / 256784L)) * hashNum);
        assertEquals(expectErrorRate, bloom.getCurrentErrorRate(), 0.0001);
    }

    @Test
    public void testAddElem() throws Exception {
        System.out.println("testAddElem");

        BloomFilter<UUID> bloom = new SimpleBloomFilter<UUID>(1000, 0.021);
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            bytes[i] = (byte) i;
        }
        bloom.addElem(bytes, 8, 16);
        assertEquals(1, bloom.getCurrentElemNum());

        bloom.addElem(bytes);
        assertEquals(2, bloom.getCurrentElemNum());

        bloom.addElem(UUID.randomUUID());
        assertEquals(3, bloom.getCurrentElemNum());

        bloom.addElem(UUID.randomUUID());
        assertEquals(4, bloom.getCurrentElemNum());
    }

    @Test
    public void testMayContain() throws Exception {
        System.out.println("testMayContain");

        BloomFilter<UUID> bloom = new SimpleBloomFilter<UUID>(1000, 0.021);
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            bytes[i] = (byte) i;
        }
        bloom.addElem(bytes, 8, 16);
        bloom.addElem(bytes);
        UUID uuid = UUID.randomUUID();
        bloom.addElem(uuid);

        boolean result = bloom.mayContain(bytes, 8, 16);
        assertTrue(result);

        result = bloom.mayContain(bytes);
        assertTrue(result);

        result = bloom.mayContain(uuid);
        assertTrue(result);

        result = bloom.mayContain(UUID.randomUUID());
        assertFalse(result);
    }

    @Test
    public void testClear() throws Exception {
        System.out.println("testClear");
        BloomFilter<UUID> bloom = new SimpleBloomFilter<UUID>(1000, 0.021);
        for (int i = 0; i < 15; i++) {
            bloom.addElem(UUID.randomUUID());
        }
        assertEquals(15, bloom.getCurrentElemNum());
        assertFalse(bloom.isEmpty());
        bloom.clear();
        assertEquals(0, bloom.getCurrentElemNum());
        assertTrue(bloom.isEmpty());
    }

    @Test
    public void testIsEmpty() throws Exception {
        System.out.println("testIsEmpty");
        BloomFilter<UUID> bloom = new SimpleBloomFilter<UUID>(1000, 0.021);
        assertTrue(bloom.isEmpty());
        for (int i = 0; i < 31; i++) {
            bloom.addElem(UUID.randomUUID());
        }
        assertFalse(bloom.isEmpty());
        bloom.clear();
        assertTrue(bloom.isEmpty());
    }

    @Test
    public void testCalcErrorRate() throws Exception {
        System.out.println("testCalcErrorRate");
        double errorRate = SimpleBloomFilter.calcErrorRate(1000, 10000, 5);
        assertEquals(0.0095, errorRate, 0.0001);
    }

    @Test
    public void testCalcElemNum() throws Exception {
        System.out.println("testCalcElemNum");
        long elemNum = SimpleBloomFilter.calcElemNum(7777777L, 0.0081);
        assertEquals(775942L, elemNum);
    }

    @Test
    public void testCalcBitSize() throws Exception {
        System.out.println("testCalcBitSize");
        long bitSize = SimpleBloomFilter.calcBitSize(6666666L, 0.0081);
        assertEquals(66824304L, bitSize);
    }

    @Test
    public void testCalcHashNum() throws Exception {
        System.out.println("testCalcHashNum");
        int hashNum = SimpleBloomFilter.calcHashNum(775942L, 7777777L);
        assertEquals(7, hashNum);
    }
}
