package ar.solocuerdas.backend.conversations;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

// Lista fija en el codigo, sin UI de administracion todavia -- mismo
// criterio que el stub de moderacion de media: simple para el MVP de
// tesis, Octavio la ajusta con casos reales a medida que aparezcan.
// Matching por palabra completa (no substring), para que "armario" no
// dispare por contener "arma". Se le sacan los acentos al contenido antes
// de comparar, asi la lista no necesita variantes acentuadas.
final class BannedWordsFilter {

    private static final List<String> BANNED_TERMS = List.of(
            "droga", "drogas", "cocaina", "marihuana", "merca", "falopa", "paco",
            "arma", "armas", "pistola", "revolver", "fusil", "escopeta",
            "amenaza", "amenazas", "te voy a matar", "te mato");

    private static final Pattern PATTERN = Pattern.compile(
            "\\b(" + String.join("|", BANNED_TERMS.stream().map(Pattern::quote).toList()) + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private BannedWordsFilter() {
    }

    static boolean containsBannedWord(String content) {
        return PATTERN.matcher(stripAccents(content)).find();
    }

    private static String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
