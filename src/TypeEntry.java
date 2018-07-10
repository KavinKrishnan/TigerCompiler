import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Queue;
import java.util.regex.Pattern;

public class TypeEntry extends SymbolEntry {
    public boolean isAnotherType;
    public String anotherType;

    public boolean isInt;

    public boolean isArray;
    public int arrSize;

    public boolean isRecord;
    public ArrayList<String> recordNames;
    public ArrayList<Boolean> isIntFields; //seems pretty stupid but whatever, lol

    public TypeEntry(String name) {
        super(name, SymbolType.TYPE);
    }

    @Override
    public void setType(String type) {
        if (type.equals("int")) {
            isAnotherType = false;
            isInt = true;
        } else if (type.equals("float")) {
            isAnotherType = false;
            isInt = false;
        } else if (type.equals("array")) {
            isAnotherType = false;
            isArray = true;
            isRecord = false;
        } else if (type.equals("record")) {
            isAnotherType = false;
            isArray = false;
            isRecord = true;
        } else {
            isAnotherType = true;
            isArray = false;
            isRecord = false;
            anotherType = type;
        }
    }

    @Override
    public String toString() {
        String s;
        s = name + " | " + symbolType;
        if (isArray) {
            s += " | Array [" + arrSize + "] of ";
            if (isInt) {
                s += "int";
            } else {
                s += "float";
            }
        } else if (isRecord) {
            s += " | Record with fields:";
            for (int i = 0; i < recordNames.size(); ++i) {
                s += " " + recordNames.get(i) + ":";
                if (isIntFields.get(i)) {
                    s += "int;";
                } else {
                    s += "float;";
                }
            }
        } else if (isAnotherType) {
            s += " | " + anotherType;
        } else {
            if (isInt) {
                s += " | int";
            } else {
                s += " | float";
            }
        }
        return s;
    }
}