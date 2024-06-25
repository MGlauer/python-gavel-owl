package testClassExpressionTranslator;

import fol.*;
import org.junit.jupiter.params.provider.Arguments;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataRange;
import translation.OWLDataTranslator;
import translation.OWLPropertyExpressionTranslator;
import translation.OWLClassExpressionTranslator;

import java.util.stream.Stream;

public class TestDataAllValuesFrom extends StandardClassExpressionTest {

    public static Stream<Arguments> provideTestCases() {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        OWLDataPropertyExpression dpe = df.getOWLDataProperty("dpe");
        OWLDataRange dr = df.getOWLDatatype("dr");

        return Stream.of(
            Arguments.of(
                df.getOWLDataAllValuesFrom(dpe, dr),
                new QuantifiedFormula(
                    new Quantifier(0), // universal quantifier
                    new Variable[]{x},
                    new BinaryFormula(
                        df.getOWLThing().accept(new OWLClassExpressionTranslator(z)),
                        new BinaryConnective(0), // 0 = conjunction
                        new BinaryFormula(
                            dpe.accept(new OWLPropertyExpressionTranslator(z, x)),
                            new BinaryConnective(3), // implication
                            dr.accept(new OWLDataTranslator(x))
                        )
                    )
                )
            )
        );
    }
}
