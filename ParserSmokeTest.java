import cn.edu.hitsz.compiler.lexer.LexicalAnalyzer;
import cn.edu.hitsz.compiler.lexer.TokenKind;
import cn.edu.hitsz.compiler.parser.ProductionCollector;
import cn.edu.hitsz.compiler.parser.SyntaxAnalyzer;
import cn.edu.hitsz.compiler.parser.table.GrammarInfo;
import cn.edu.hitsz.compiler.parser.table.TableLoader;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FilePathConfig;

public class ParserSmokeTest {
    public static void main(String[] args) {
        TokenKind.loadTokenKinds();

        final var symbolTable = new SymbolTable();
        final var lexer = new LexicalAnalyzer(symbolTable);
        lexer.loadFile(FilePathConfig.SRC_CODE_PATH);
        lexer.run();

        final var lrTable = new TableLoader().load(FilePathConfig.LR1_TABLE_PATH);
        final var parser = new SyntaxAnalyzer(symbolTable);
        parser.loadTokens(lexer.getTokens());
        parser.loadLRTable(lrTable);

        final var productionCollector = new ProductionCollector(GrammarInfo.getBeginProduction());
        parser.registerObserver(productionCollector);
        parser.run();

        productionCollector.dumpToFile(FilePathConfig.PARSER_PATH);
    }
}
