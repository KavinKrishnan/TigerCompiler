import java.io.*;

public class Tiger {
    public static void main(String[] args)  throws FileNotFoundException, IOException {

        if (args.length < 1)
        {
            System.out.println("Error, you must provide the filepath to a valid tiger program");
        }
        else
        {
            TigerScanner scanner = new TigerScanner(args[0]);
            SymbolTable symbolTable = new SymbolTable();
            TigerParser parser = new TigerParser(scanner, symbolTable);
            parser.parse();
            symbolTable.printTable();
            try {
                System.out.println('\n' + parser.ir.toString());
            }
            catch (Exception e)
            {

            }
        }

    }
}