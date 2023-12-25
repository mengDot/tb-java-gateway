package com.meng.tbjavagateway.lms;

import java.util.ArrayList;
import java.util.List;

public class ByteArrayConveter {
    public static List<Float> getFloatArray(int[] values) {
        List<Float> floatList = new ArrayList<>();
        for (int value : values) {
            float floatValue = Float.intBitsToFloat(value);
            floatList.add(floatValue);
        }
        return floatList;
    }
}
