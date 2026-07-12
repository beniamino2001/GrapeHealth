package it.pegasopw.grapehealth.decisionengine.regole.support;

public class SogliaConIsteresi {

    public enum Verso {
        PEGGIORA_SALENDO,
        PEGGIORA_SCENDENDO
    }

    private final double sogliaModerato;
    private final Double sogliaSevero; // null se la regola prevede un solo livello di rischio
    private final double isteresi;
    private final Verso verso;

    private SogliaConIsteresi(double sogliaModerato, Double sogliaSevero, double isteresi, Verso verso) {
        this.sogliaModerato = sogliaModerato;
        this.sogliaSevero = sogliaSevero;
        this.isteresi = isteresi;
        this.verso = verso;
    }

    // Per regole con due livelli di rischio (es. stress idrico: moderato/severo).
    public static SogliaConIsteresi dueSoglie(double sogliaModerato, double sogliaSevero, double isteresi, Verso verso) {
        return new SogliaConIsteresi(sogliaModerato, sogliaSevero, isteresi, verso);
    }

    // Per regole con un solo livello di rischio poichè in bibliografia hanno definito un'unica soglia di riferimento.
    public static SogliaConIsteresi sogliaSingola(double sogliaModerato, double isteresi, Verso verso) {
        return new SogliaConIsteresi(sogliaModerato, null, isteresi, verso);
    }

    public double sogliaModerato() {
        return sogliaModerato;
    }

    public Double sogliaSevero() {
        return sogliaSevero;
    }

    public String calcolaLivello(double valore, String livelloPrecedente) {
        boolean eraSevero = sogliaSevero != null && "severo".equals(livelloPrecedente);
        boolean eraModerato = eraSevero || "moderato".equals(livelloPrecedente);

        if (sogliaSevero != null) {
            double sogliaUscitaSevero = sogliaSevero - segno() * isteresi;
            if (oltre(valore, sogliaSevero) || (eraSevero && oltre(valore, sogliaUscitaSevero))) {
                return "severo";
            }
        }

        double sogliaUscitaModerato = sogliaModerato - segno() * isteresi;
        if (oltre(valore, sogliaModerato) || (eraModerato && oltre(valore, sogliaUscitaModerato))) {
            return "moderato";
        }
        return null;
    }

    private boolean oltre(double valore, double soglia) {
        return verso == Verso.PEGGIORA_SALENDO ? valore > soglia : valore < soglia;
    }

    private int segno() {
        return verso == Verso.PEGGIORA_SALENDO ? 1 : -1;
    }
}