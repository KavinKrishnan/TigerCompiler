public class TigerToken {
    private String lit;
    private TokenEnum token;
    private int lineNum;

    public TigerToken(String lit, TokenEnum token, int lineNum) {
        this.lit = lit;
        this.token = token;
        this.lineNum = lineNum;
    }

    public TokenEnum getToken() {
        return token;
    }

    public String getLit() {
        return lit;
    }

    public int getLine() {
        return lineNum;
    }
}