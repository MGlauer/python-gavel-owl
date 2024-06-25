package testClassExpressionTranslator;

import fol.*;
import org.junit.jupiter.params.provider.Arguments;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import translation.OWLClassExpressionTranslator;
import translation.OWLPropertyExpressionTranslator;

import java.util.stream.Stream;

public class TestObjectAllValuesFrom extends StandardClassExpressionTest {

    private static Stream<Arguments> provideTestCases() {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        OWLClass testClass0 = df.getOWLClass("Class0");
        OWLObjectPropertyExpression testProperty0 = df.getOWLObjectProperty("Prop0");
        LogicElement[] variables = {z};

        return Stream.of(
                //test AllValuesFrom
                Arguments.of(
                        df.getOWLObjectAllValuesFrom(testProperty0, testClass0),
                        new QuantifiedFormula(
                                new Quantifier(0),
                                new Variable[]{x},
                                new BinaryFormula(
                                        df.getOWLThing().accept(new OWLClassExpressionTranslator(z)),
                                        new BinaryConnective(0),
                                        new BinaryFormula(
                                                testProperty0.accept(new OWLPropertyExpressionTranslator(z, x)),
                                                new BinaryConnective(3),
                                                testClass0.accept(new OWLClassExpressionTranslator(x))
                                        )
                                )
                        )


                )

        );
    }
}