package translation;

import fol.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.stream.Stream;

// Class expressions correspond to section 8 in OWLFS (https://www.w3.org/TR/owl2-syntax/)
public class OWLClassExpressionTranslator extends OWLTranslator implements OWLClassExpressionVisitorEx<LogicElement> {
    private final Symbol p; // first parameter to keep track of substitutions, it is a variable or constant

    public OWLClassExpressionTranslator(Symbol p) {
        this.p = p;
    }


    //Class
    public LogicElement visit(@Nonnull OWLClass owlClass) {
        return new PredicateExpression(getEntityName(owlClass), new LogicElement[]{p});
    }

    //Object Intersection Of
    public LogicElement visit(@Nonnull OWLObjectIntersectionOf intersectionOf) {
        Stream<OWLClassExpression> conj = intersectionOf.conjunctSet();
        Stream<LogicElement> stream = conj.map(x -> x.accept(new OWLClassExpressionTranslator(p)));

        return interlinkBinaryFormulas(0, stream); // 0 = conjunction
    }

    //Object Union Of
    public LogicElement visit(@Nonnull OWLObjectUnionOf unionOf) {
        Stream<OWLClassExpression> disj = unionOf.disjunctSet();
        Stream<LogicElement> stream = disj.map(x -> x.accept(new OWLClassExpressionTranslator(p)));

        return interlinkBinaryFormulas(1, stream); // 1 = disjunction
    }

    //Object Complement Of
    public LogicElement visit(@Nonnull OWLObjectComplementOf complementOf) {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        return new BinaryFormula(
            new UnaryFormula(
                new UnaryConnective(0), //negation
                complementOf.getOperand().accept(new OWLClassExpressionTranslator(p))),
            new BinaryConnective(0), // conjunction
            df.getOWLThing().accept(new OWLClassExpressionTranslator(p))
        );
    }

    //Object One Of
    public LogicElement visit(@Nonnull OWLObjectOneOf oneOf) {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        Stream<LogicElement> stream = oneOf.individuals().map(x ->
            new BinaryFormula(
                p,
                new BinaryConnective(8), // 8 = EQ
                x.accept(new OWLIndividualTranslator())
            ));
        return interlinkBinaryFormulas(1, stream); // 1 = disjunction
    }

    //Object Some Values From
    @Override
    public LogicElement visit(OWLObjectSomeValuesFrom ce) {
        Variable x = getUniqueVariable();
        return new QuantifiedFormula(
            new Quantifier(1), // 1 = existential quantifier
            new Variable[]{x},
            new BinaryFormula(
                ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, x)),
                new BinaryConnective(0), // 0 = conjunction
                ce.getFiller().accept(new OWLClassExpressionTranslator(x))));
    }

    //Object All Values From
    @Override
    public LogicElement visit(OWLObjectAllValuesFrom ce) {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        Variable x = getUniqueVariable();
        return new QuantifiedFormula(
            new Quantifier(0), // 0 = universal quantifier
            new Variable[]{x},
            new BinaryFormula(
                df.getOWLThing().accept(new OWLClassExpressionTranslator(p)),
                new BinaryConnective(0), // 0 = conjunction
                new BinaryFormula(
                    ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, x)),
                    new BinaryConnective(3), // 3 = implication
                    ce.getFiller().accept(new OWLClassExpressionTranslator(x)))));
    }

    //Object Has Value
    @Override
    public LogicElement visit(OWLObjectHasValue ce) {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        return new BinaryFormula(
            ce.getProperty().accept(
                new OWLPropertyExpressionTranslator(p, ce.getFiller().accept(new OWLIndividualTranslator()))),
            new BinaryConnective(0),
            df.getOWLThing().accept(
                new OWLClassExpressionTranslator(ce.getFiller().accept(new OWLIndividualTranslator())))
        );
    }

    //Object Has Self
    @Override
    public LogicElement visit(OWLObjectHasSelf ce) {
        return ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, p));
    }

    //Object Min Cardinality
    @Override
    public LogicElement visit(OWLObjectMinCardinality ce) {
        if (ce.getCardinality() == 0) return new DefinedConstant(0); // $true

        Variable[] vars = new Variable[ce.getCardinality()];
        for (int ind = 0; ind < ce.getCardinality(); ind++) {
            vars[ind] = getUniqueVariable();
        }

        ArrayList<LogicElement> elemList = new ArrayList<>();
        for (int i = 0; i < vars.length - 1; i++) {
            for (int j = i + 1; j < vars.length; j++) {
                elemList.add(new BinaryFormula(vars[i], new BinaryConnective(9), vars[j])); // 9 = NEQ
            }
        }
        for (Variable var : vars) {
            elemList.add(ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, var)));
            elemList.add(ce.getFiller().accept(new OWLClassExpressionTranslator(var)));
        }
        LogicElement conjunction = interlinkBinaryFormulas(0, elemList.stream()); // 0 = conjunction
        return new QuantifiedFormula(new Quantifier(1), vars, conjunction); // 1 = existential quantifier
    }

    //Object Max Cardinality
    @Override
    public LogicElement visit(OWLObjectMaxCardinality ce) {
        //create needed quantified variables
        Variable[] vars = new Variable[ce.getCardinality() + 1];
        for (int ind = 0; ind < vars.length; ind++) {
            vars[ind] = getUniqueVariable();
        }

        //assign each quantified variable the class and the relation
        ArrayList<LogicElement> premiseList = new ArrayList<>();
        for (Variable var : vars) {
            premiseList.add(ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, var)));
            premiseList.add(ce.getFiller().accept(new OWLClassExpressionTranslator(var)));
        }

        //create an axiom for each pair of variables stating they are not equal
        ArrayList<LogicElement> conclusionList = new ArrayList<>();
        for (int i = 0; i < vars.length - 1; i++) {
            for (int j = i + 1; j < vars.length; j++) {
                conclusionList.add(new BinaryFormula(vars[i], new BinaryConnective(9), vars[j])); // 9 = NEQ
            }
        }

        LogicElement premise = interlinkBinaryFormulas(0, premiseList.stream()); // 0 = conjunction
        //special case: cardinality is 0 -> no instances of premise possible
        if (ce.getCardinality() == 0) {
            return new QuantifiedFormula(
                new Quantifier(0), // universal quantifier
                vars,
                new UnaryFormula(new UnaryConnective(0), premise));
        }
        //combine premises and conclusions
        UnaryFormula conclusion = new UnaryFormula(
            new UnaryConnective(0), // 0 = negation
            interlinkBinaryFormulas(0, conclusionList.stream())); // 0 = conjunction
        return new QuantifiedFormula(
            new Quantifier(0),  // 0 = universal quantifier
            vars,
            new BinaryFormula(premise, new BinaryConnective(3), conclusion)); // 3 = implication
    }

    //Object Exact Cardinality
    @Override
    public LogicElement visit(OWLObjectExactCardinality ce) {
        int n = ce.getCardinality();
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        OWLObjectMinCardinality min = df.getOWLObjectMinCardinality(n, ce.getProperty(), ce.getFiller());
        OWLObjectMaxCardinality max = df.getOWLObjectMaxCardinality(n, ce.getProperty(), ce.getFiller());
        OWLObjectIntersectionOf intersection = df.getOWLObjectIntersectionOf(min, max);
        return intersection.accept(new OWLClassExpressionTranslator(p));
    }

    // Data Some Values From
    @Override
    public LogicElement visit(OWLDataSomeValuesFrom ce) {
        Variable x = getUniqueVariable();
        return new QuantifiedFormula(
            new Quantifier(1), // existential quantifier
            new Variable[]{x},
            new BinaryFormula(
                ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, x)),
                new BinaryConnective(0), // conjunction
                ce.getFiller().accept(new OWLDataTranslator(x))
            )
        );
    }

    // Data All Values From
    @Override
    public LogicElement visit(OWLDataAllValuesFrom ce) {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        Variable x = getUniqueVariable();
        return new QuantifiedFormula(
            new Quantifier(0), // universal quantifier
            new Variable[]{x},
            new BinaryFormula(
                df.getOWLThing().accept(new OWLClassExpressionTranslator(p)),
                new BinaryConnective(0), // 0 = conjunction
                new BinaryFormula(
                    ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, x)),
                    new BinaryConnective(3), // implication
                    ce.getFiller().accept(new OWLDataTranslator(x))
                )
            )
        );
    }

    // Data Has Value
    @Override
    public LogicElement visit(OWLDataHasValue ce) {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        return new BinaryFormula(
            ce.getProperty().accept(
                new OWLPropertyExpressionTranslator(p, ce.getFiller().accept(new OWLLiteralTranslator()))),
            new BinaryConnective(0),
            df.getTopDatatype().accept(
                new OWLDataTranslator(ce.getFiller().accept(new OWLLiteralTranslator()))));
    }

    // Data Min Cardinality
    @Override
    public LogicElement visit(OWLDataMinCardinality ce) {
        if (ce.getCardinality() == 0) return new DefinedConstant(0);

        Variable[] vars = new Variable[ce.getCardinality()];
        for (int ind = 0; ind < ce.getCardinality(); ind++) {
            vars[ind] = getUniqueVariable();
        }

        ArrayList<LogicElement> elemList = new ArrayList<>();
        for (int i = 0; i < vars.length - 1; i++) {
            for (int j = i + 1; j < vars.length; j++) {
                elemList.add(new BinaryFormula(vars[i], new BinaryConnective(9), vars[j])); // 9 = NEQ
            }
        }
        for (Variable var : vars) {
            elemList.add(ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, var)));
            elemList.add(ce.getFiller().accept(new OWLDataTranslator(var)));
        }
        LogicElement conjunction = interlinkBinaryFormulas(0, elemList.stream()); // 0 = conjunction
        return new QuantifiedFormula(new Quantifier(1), vars, conjunction); // 1 = existential quantifier
    }

    // Data Max Cardinality
    @Override
    public LogicElement visit(OWLDataMaxCardinality ce) {
        //create needed quantified variables
        Variable[] vars = new Variable[ce.getCardinality() + 1];
        for (int ind = 0; ind < vars.length; ind++) {
            vars[ind] = getUniqueVariable();
        }

        //assign each quantified variable the class and the relation
        ArrayList<LogicElement> premiseList = new ArrayList<>();
        for (Variable var : vars) {
            premiseList.add(ce.getProperty().accept(new OWLPropertyExpressionTranslator(p, var)));
            premiseList.add(ce.getFiller().accept(new OWLDataTranslator(var)));
        }

        //create an axiom for each pair of variables stating they are not equal
        ArrayList<LogicElement> conclusionList = new ArrayList<>();
        for (int i = 0; i < vars.length - 1; i++) {
            for (int j = i + 1; j < vars.length; j++) {
                conclusionList.add(new BinaryFormula(vars[i], new BinaryConnective(9), vars[j])); // 9 = NEQ
            }
        }

        LogicElement premise = interlinkBinaryFormulas(0, premiseList.stream()); // 0 = conjunction
        //special case: cardinality is 0 -> no instances of premise possible
        if (ce.getCardinality() == 0) {
            return new QuantifiedFormula(
                new Quantifier(0), // universal quantifier
                vars,
                new UnaryFormula(new UnaryConnective(0), premise));
        }
        //combine premises and conclusions
        UnaryFormula conclusion = new UnaryFormula(
            new UnaryConnective(0), // 0 = negation
            interlinkBinaryFormulas(0, conclusionList.stream())); // 0 = conjunction
        return new QuantifiedFormula(
            new Quantifier(0),  // 0 = universal quantifier
            vars,
            new BinaryFormula(premise, new BinaryConnective(3), conclusion)); // 3 = implication
    }

    // Data Exact Cardinality
    @Override
    public LogicElement visit(OWLDataExactCardinality ce) {
        return ce.asIntersectionOfMinMax().accept(new OWLClassExpressionTranslator(p));
    }
}
