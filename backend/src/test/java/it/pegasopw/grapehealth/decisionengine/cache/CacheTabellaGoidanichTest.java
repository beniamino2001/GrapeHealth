package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichEntity;
import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichId;
import it.pegasopw.grapehealth.decisionengine.repository.SogliaIncubazioneGoidanichRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheTabellaGoidanichTest {

    private SogliaIncubazioneGoidanichEntity riga(int temperatura, boolean umiditaAlta, double percentuale) {
        SogliaIncubazioneGoidanichEntity entity = new SogliaIncubazioneGoidanichEntity();
        entity.setId(new SogliaIncubazioneGoidanichId(temperatura, umiditaAlta));
        entity.setPercentualeIncrementoGiornaliero(percentuale);
        return entity;
    }

    private CacheTabellaGoidanich cacheDiTest() {
        SogliaIncubazioneGoidanichRepository repository = mock(SogliaIncubazioneGoidanichRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                riga(14, false, 6.6), riga(14, true, 9.0),
                riga(20, false, 14.2), riga(20, true, 20.0),
                riga(23, false, 18.1), riga(23, true, 25.0),
                riga(26, false, 16.6), riga(26, true, 16.6)));

        CacheTabellaGoidanich cache = new CacheTabellaGoidanich(repository);
        cache.carica();
        return cache;
    }

    @Test
    void restituisceIlValoreEsattoDiTabellaPerUmiditaAlta() {
        assertEquals(20.0, cacheDiTest().percentualeGiornaliera(20.0, 95.0), 0.001);
    }

    @Test
    void restituisceIlValoreEsattoDiTabellaPerUmiditaBassa() {
        assertEquals(14.2, cacheDiTest().percentualeGiornaliera(20.0, 50.0), 0.001);
    }

    @Test
    void usaNovantaPerCentoComeSpartiacqueTraUmiditaBassaEAlta() {
        assertEquals(18.1, cacheDiTest().percentualeGiornaliera(23.0, 89.9), 0.001);
        assertEquals(25.0, cacheDiTest().percentualeGiornaliera(23.0, 90.0), 0.001);
    }

    @Test
    void estrapolaSottoLaTemperaturaPiuBassaCaricata() {
        assertEquals(6.6, cacheDiTest().percentualeGiornaliera(10.0, 50.0), 0.001);
        assertEquals(6.6, cacheDiTest().percentualeGiornaliera(13.9, 50.0), 0.001);
    }

    @Test
    void usaLaRigaPiuAltaCaricataSopraIlMassimo() {
        assertEquals(16.6, cacheDiTest().percentualeGiornaliera(30.0, 50.0), 0.001);
    }

    @Test
    void arrotondaPerDifettoSenzaInterpolareTraRighe() {
        assertEquals(14.2, cacheDiTest().percentualeGiornaliera(20.9, 50.0), 0.001);
    }
}