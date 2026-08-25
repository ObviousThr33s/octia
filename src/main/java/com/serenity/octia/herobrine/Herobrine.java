package com.serenity.octia.herobrine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Herobrine - a director for haunt moments, and the announcer that speaks them.
 *
 * This is the C prototype at prototype/herobrine, carried into Java as one file
 * and one package. Its own package on purpose: nothing here touches Minecraft,
 * Fabric or the rest of the mod, and nothing in the mod reaches in.
 *
 * Pipeline: source --lines--> statements --parse--> haunts --run--> director
 *           text   --rules--> phonemes  --shape--> a tube  --waves--> PA --> pcm
 *
 * What the C version had and this does not: the GoldSrc rcon link, the
 * WebSocket server and witness page, the /say/<word>.wav endpoint, the reading
 * of a Valve vox install, and the vocabulary export that fed a machine without
 * one. Those were all hooks into something outside, and they are gone. The
 * night is text on stdout; the voice is reachable through speak().
 *
 * Because the install is gone, every vox line is synthesised. There is no
 * fragment lookup left to miss a word on, so a world either parses or does not.
 *
 * run: java com.serenity.octia.herobrine.Herobrine --check world.haunt
 *      java ... --dry world.haunt          run the night to stdout at once
 *      java ... --dry --fast world.haunt   same night, 50 ms to the second
 *      java ... --speak "attention." --out say.wav
 *      java ... --pronounce herobrine
 *      java ... --repl                     type a line, write a wav, hear it
 *
 * language: one statement per line, first word is the verb, the rest is words,
 *           numbers or a sentence. no braces, no quotes, # to end of line.
 *             world <words>            the name of the night
 *             seed <n>                 same seed replays the same night exactly
 *             era <year>               1950, 1962, 1980 or 1990
 *             haunt <name>             opens a haunt; the lines below belong to it
 *             after <n> seconds        earliest it may fire
 *             after <haunt>            not until that haunt has fired
 *             chance <n> in <m>        rolled once per tick, once eligible
 *             once                     fires at most once a night
 *             urgent                   spoken tighter, higher and faster
 *             where <words>            told to whatever is listening
 *             vox <sentence>           spoken by the announcer
 *             do <words>               an action for a client to carry out
 *             say <sentence>           text, for a client with no voice
 */
public final class Herobrine {

    private Herobrine() {
    }

    // ================================================================= the voice

    /** What HL1's own vox fragments were, and what the wav writer claims. */
    public static final int RATE = 22050;

    private static final int K_SIL = 0, K_VOWEL = 1, K_NASAL = 2,
            K_LIQUID = 3, K_FRIC = 4, K_STOP = 5;

    /**
     * A phoneme is a shape, not a set of frequencies. Everything below describes
     * where the tongue is and where the tube is pinched; the sound is whatever a
     * tube of that shape does when you blow through it.
     *
     *   ti, td   where the tongue hump sits (0=glottis .. 43=lips) and how wide
     *            the tube is left there, in the same units as everything else
     *   ci, cd   a tighter pinch on top of that, where the consonants live.
     *            cd < 0 means no pinch at all
     *   lip      the last three cylinders scaled: rounding
     *   velum    0 shut, 1 open to the nose
     *   noise    turbulence injected at the pinch, where fricatives are made
     */
    private record Phone(String name, int kind, double ti, double td,
                         double ci, double cd, double lip, double velum,
                         double gi, double gd, double dur, double amp,
                         boolean voiced, double noise) {
    }

    /**
     * Fitted by measurement against adult female formants, the announcer being a
     * woman: ring the tube with an impulse and read where it rings.
     *
     * td is the width the tongue leaves the tube at its hump. A tongue narrows a
     * throat, it does not widen one. High front vowels pinch near the palate
     * (high ti, small td); low back vowels pinch in the pharynx (low ti).
     */
    private static final Phone[] PHONES = {
            //          name  kind       ti    td     ci    cd    lip  vel    gi    gd   dur   amp   vcd    nz
            new Phone("AA", K_VOWEL, 15.0, 0.50, 0.0, -1.0, 1.00, 0.0, 0.0, 0.0, 140, 1.00, true, 0.00),
            new Phone("AE", K_VOWEL, 11.0, 1.55, 0.0, -1.0, 0.85, 0.0, 0.0, 0.0, 140, 1.00, true, 0.00),
            new Phone("AH", K_VOWEL, 16.0, 0.65, 0.0, -1.0, 1.00, 0.0, 0.0, 0.0, 105, 0.90, true, 0.00),
            new Phone("AO", K_VOWEL, 26.0, 0.80, 0.0, -1.0, 0.90, 0.0, 0.0, 0.0, 140, 1.00, true, 0.00),
            new Phone("EH", K_VOWEL, 26.0, 1.25, 0.0, -1.0, 0.95, 0.0, 0.0, 0.0, 130, 1.00, true, 0.00),
            new Phone("ER", K_VOWEL, 20.0, 0.50, 0.0, -1.0, 0.85, 0.0, 0.0, 0.0, 150, 0.95, true, 0.00),
            new Phone("IH", K_VOWEL, 37.0, 1.10, 0.0, -1.0, 0.85, 0.0, 0.0, 0.0, 105, 0.95, true, 0.00),
            new Phone("IY", K_VOWEL, 30.0, 0.35, 0.0, -1.0, 1.00, 0.0, 0.0, 0.0, 140, 0.95, true, 0.00),
            new Phone("UH", K_VOWEL, 21.0, 0.50, 0.0, -1.0, 0.90, 0.0, 0.0, 0.0, 105, 0.90, true, 0.00),
            new Phone("UW", K_VOWEL, 25.0, 0.50, 0.0, -1.0, 0.75, 0.0, 0.0, 0.0, 140, 0.95, true, 0.00),
            // diphthongs: the tongue is somewhere else by the end of the sound
            new Phone("AY", K_VOWEL, 15.0, 0.50, 0.0, -1.0, 1.00, 0.0, 30.0, 0.35, 190, 1.00, true, 0.00),
            new Phone("EY", K_VOWEL, 26.0, 1.25, 0.0, -1.0, 0.95, 0.0, 30.0, 0.35, 180, 1.00, true, 0.00),
            new Phone("OW", K_VOWEL, 26.0, 0.80, 0.0, -1.0, 0.90, 0.0, 25.0, 0.50, 180, 1.00, true, 0.00),
            new Phone("AW", K_VOWEL, 15.0, 0.50, 0.0, -1.0, 1.00, 0.0, 25.0, 0.50, 190, 1.00, true, 0.00),
            new Phone("OY", K_VOWEL, 26.0, 0.80, 0.0, -1.0, 0.90, 0.0, 30.0, 0.35, 200, 1.00, true, 0.00),
            // nasals: the mouth is shut somewhere and the velum is open
            new Phone("M", K_NASAL, 17.0, 2.20, 42.0, 0.00, 1.00, 1.0, 0.0, 0.0, 85, 0.75, true, 0.00),
            new Phone("N", K_NASAL, 20.0, 2.20, 35.0, 0.00, 1.00, 1.0, 0.0, 0.0, 85, 0.75, true, 0.00),
            new Phone("NG", K_NASAL, 20.0, 2.20, 24.0, 0.00, 1.00, 1.0, 0.0, 0.0, 95, 0.75, true, 0.00),

            new Phone("L", K_LIQUID, 20.0, 2.20, 34.0, 1.05, 1.00, 0.0, 0.0, 0.0, 80, 0.85, true, 0.00),
            new Phone("R", K_LIQUID, 22.0, 1.60, 27.0, 1.25, 0.90, 0.0, 0.0, 0.0, 80, 0.85, true, 0.00),
            new Phone("W", K_LIQUID, 19.0, 1.00, 41.0, 1.20, 0.60, 0.0, 0.0, 0.0, 70, 0.80, true, 0.00),
            new Phone("Y", K_LIQUID, 27.0, 1.00, 28.0, 1.20, 1.00, 0.0, 0.0, 0.0, 70, 0.80, true, 0.00),
            // fricatives: a tight pinch, and air made turbulent at it
            new Phone("F", K_FRIC, 17.0, 2.40, 42.0, 0.42, 0.90, 0.0, 0.0, 0.0, 95, 0.55, false, 0.85),
            new Phone("TH", K_FRIC, 20.0, 2.40, 37.0, 0.52, 1.00, 0.0, 0.0, 0.0, 95, 0.50, false, 0.75),
            new Phone("S", K_FRIC, 22.0, 2.20, 35.5, 0.34, 1.00, 0.0, 0.0, 0.0, 105, 0.70, false, 1.30),
            new Phone("SH", K_FRIC, 23.0, 2.10, 31.0, 0.50, 0.85, 0.0, 0.0, 0.0, 115, 0.75, false, 1.20),
            new Phone("HH", K_FRIC, 17.0, 2.60, 2.0, 0.90, 1.00, 0.0, 0.0, 0.0, 70, 0.40, false, 0.60),
            new Phone("V", K_FRIC, 17.0, 2.40, 42.0, 0.45, 0.90, 0.0, 0.0, 0.0, 75, 0.60, true, 0.40),
            new Phone("DH", K_FRIC, 20.0, 2.40, 37.0, 0.55, 1.00, 0.0, 0.0, 0.0, 75, 0.60, true, 0.35),
            new Phone("Z", K_FRIC, 22.0, 2.20, 35.5, 0.38, 1.00, 0.0, 0.0, 0.0, 90, 0.70, true, 0.70),
            new Phone("ZH", K_FRIC, 23.0, 2.10, 31.0, 0.52, 0.85, 0.0, 0.0, 0.0, 95, 0.70, true, 0.65),
            // stops: the tube shuts and then opens. the silence is the sound.
            new Phone("P", K_STOP, 17.0, 2.40, 43.0, 0.00, 1.00, 0.0, 0.0, 0.0, 80, 0.80, false, 1.10),
            new Phone("T", K_STOP, 22.0, 2.20, 35.5, 0.00, 1.00, 0.0, 0.0, 0.0, 80, 0.80, false, 1.25),
            new Phone("K", K_STOP, 20.0, 2.20, 24.0, 0.00, 1.00, 0.0, 0.0, 0.0, 85, 0.80, false, 1.15),
            new Phone("B", K_STOP, 17.0, 2.40, 43.0, 0.00, 1.00, 0.0, 0.0, 0.0, 70, 0.70, true, 0.45),
            new Phone("D", K_STOP, 22.0, 2.20, 35.5, 0.00, 1.00, 0.0, 0.0, 0.0, 70, 0.70, true, 0.50),
            new Phone("G", K_STOP, 20.0, 2.20, 24.0, 0.00, 1.00, 0.0, 0.0, 0.0, 75, 0.70, true, 0.48),

            new Phone("_", K_SIL, 17.0, 2.40, 0.0, -1.0, 1.00, 0.0, 0.0, 0.0, 90, 0.00, false, 0.00),
    };

    private static Phone phoneNamed(String name) {
        for (Phone p : PHONES) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------- letters to sounds

    /**
     * English spelling does not map cleanly to its sounds, so this is rules plus
     * a short list of the words we care about that the rules get wrong.
     *
     * Contexts: '#' a vowel, '^' a consonant, '$' the word edge. The table is
     * ordered longest-match-first, and the first rule that fits at a position
     * wins.
     */
    private record Rule(String left, String match, String right, String out) {
    }

    private static final Rule[] RULES = {
            // the ones that are simply words
            new Rule("$", "herobrine", "$", "HH EH R OW B R AY N"),
            new Rule("$", "mesa", "$", "M EY S AH"),
            new Rule("$", "xen", "$", "Z EH N"),
            new Rule("$", "gordon", "$", "G AO R D AH N"),
            new Rule("$", "freeman", "$", "F R IY M AH N"),
            new Rule("$", "anomalous", "$", "AH N AA M AH L AH S"),
            new Rule("$", "resonance", "$", "R EH Z AH N AH N S"),
            new Rule("$", "cascade", "$", "K AE S K EY D"),
            new Rule("$", "personnel", "$", "P ER S AH N EH L"),
            new Rule("$", "evacuate", "$", "IH V AE K Y UW EY T"),
            new Rule("$", "biohazard", "$", "B AY OW HH AE Z ER D"),
            new Rule("$", "sector", "$", "S EH K T ER"),
            new Rule("$", "ceiling", "$", "S IY L IH NG"),
            new Rule("$", "tunnel", "$", "T AH N AH L"),
            new Rule("$", "someone", "$", "S AH M W AH N"),
            new Rule("$", "one", "$", "W AH N"),
            new Rule("$", "two", "$", "T UW"),
            new Rule("$", "of", "$", "AH V"),
            new Rule("$", "the", "$", "DH AH"),
            new Rule("$", "to", "$", "T UW"),
            new Rule("$", "you", "$", "Y UW"),
            new Rule("$", "is", "$", "IH Z"),
            new Rule("$", "are", "$", "AA R"),
            new Rule("$", "there", "$", "DH EH R"),
            new Rule("$", "behind", "$", "B IH HH AY N D"),
            new Rule("$", "warning", "$", "W AO R N IH NG"),
            new Rule("$", "attention", "$", "AH T EH N SH AH N"),
            new Rule("$", "danger", "$", "D EY N JH ER"),
            new Rule("$", "movement", "$", "M UW V M AH N T"),
            new Rule("$", "detected", "$", "D IH T EH K T IH D"),
            new Rule("$", "overhead", "$", "OW V ER HH EH D"),

            // endings, before the letters inside them get a chance
            new Rule("", "tion", "$", "SH AH N"),
            new Rule("", "sion", "$", "ZH AH N"),
            new Rule("", "ing", "$", "IH NG"),
            new Rule("", "ough", "", "AH F"),
            new Rule("", "igh", "", "AY"),

            // consonant groups
            new Rule("", "sch", "", "S K"),
            new Rule("$", "kn", "", "N"),
            new Rule("$", "wr", "", "R"),
            new Rule("", "tch", "", "CH"),
            new Rule("", "ch", "", "CH"),
            new Rule("", "sh", "", "SH"),
            new Rule("", "th", "", "TH"),
            new Rule("", "ph", "", "F"),
            new Rule("", "gh", "", ""),
            new Rule("", "ck", "", "K"),
            new Rule("", "ng", "$", "NG"),
            new Rule("", "qu", "", "K W"),
            new Rule("", "wh", "", "W"),
            new Rule("", "ss", "", "S"), new Rule("", "ll", "", "L"),
            new Rule("", "tt", "", "T"), new Rule("", "pp", "", "P"),
            new Rule("", "mm", "", "M"), new Rule("", "nn", "", "N"),
            new Rule("", "ff", "", "F"), new Rule("", "rr", "", "R"),
            new Rule("", "dd", "", "D"), new Rule("", "cc", "", "K"),

            // vowel pairs
            new Rule("", "ee", "", "IY"), new Rule("", "ea", "", "IY"),
            new Rule("", "ei", "", "IY"), new Rule("", "ie", "$", "AY"),
            new Rule("", "oo", "", "UW"), new Rule("", "ou", "", "AW"),
            new Rule("", "oi", "", "OY"), new Rule("", "oy", "", "OY"),
            new Rule("", "oa", "", "OW"), new Rule("", "ai", "", "EY"),
            new Rule("", "ay", "", "EY"), new Rule("", "au", "", "AO"),
            new Rule("", "aw", "", "AO"), new Rule("", "ow", "$", "OW"),
            new Rule("", "ew", "", "UW"),

            // a vowel, one consonant, a silent e - the "magic e" that makes mad into made
            new Rule("", "a", "^e$", "EY"),
            new Rule("", "i", "^e$", "AY"),
            new Rule("", "o", "^e$", "OW"),
            new Rule("", "u", "^e$", "UW"),
            new Rule("", "e", "^e$", "IY"),

            // c and g soften before e, i, y
            new Rule("", "c", "e", "S"), new Rule("", "c", "i", "S"), new Rule("", "c", "y", "S"),
            new Rule("", "g", "e", "JH"), new Rule("", "g", "i", "JH"), new Rule("", "g", "y", "JH"),

            // r-coloured vowels
            new Rule("", "ar", "", "AA R"), new Rule("", "or", "", "AO R"),
            new Rule("", "er", "", "ER"), new Rule("", "ir", "", "ER"), new Rule("", "ur", "", "ER"),

            // the silent final e, after the magic-e rules have had their turn
            new Rule("^", "e", "$", ""),

            // single letters - the floor everything falls through to
            new Rule("", "a", "", "AE"), new Rule("", "b", "", "B"), new Rule("", "c", "", "K"),
            new Rule("", "d", "", "D"), new Rule("", "e", "", "EH"), new Rule("", "f", "", "F"),
            new Rule("", "g", "", "G"), new Rule("", "h", "", "HH"), new Rule("", "i", "", "IH"),
            new Rule("", "j", "", "JH"), new Rule("", "k", "", "K"), new Rule("", "l", "", "L"),
            new Rule("", "m", "", "M"), new Rule("", "n", "", "N"), new Rule("", "o", "", "AA"),
            new Rule("", "p", "", "P"), new Rule("", "q", "", "K"), new Rule("", "r", "", "R"),
            new Rule("", "s", "", "S"), new Rule("", "t", "", "T"), new Rule("", "u", "", "AH"),
            new Rule("", "v", "", "V"), new Rule("", "w", "", "W"), new Rule("", "x", "", "K S"),
            new Rule("$", "y", "", "Y"), new Rule("", "y", "", "IY"), new Rule("", "z", "", "Z"),
    };

    private static boolean isVowelCh(char c) {
        return c != 0 && "aeiouy".indexOf(c) >= 0;
    }

    private static boolean ctxRightOk(String w, int pos, String ctx) {
        for (int i = 0; i < ctx.length(); i++) {
            char k = ctx.charAt(i);
            char c = pos < w.length() ? w.charAt(pos) : 0;
            if (k == '$') {
                return c == 0;
            }
            if (c == 0) {
                return false;
            }
            if (k == '#') {
                if (!isVowelCh(c)) {
                    return false;
                }
            } else if (k == '^') {
                if (isVowelCh(c) || !Character.isLetter(c)) {
                    return false;
                }
            } else if (k != c) {
                return false;
            }
            pos++;
        }
        return true;
    }

    private static boolean ctxLeftOk(String w, int pos, String ctx) {
        for (int i = ctx.length(); i-- > 0; ) {
            char k = ctx.charAt(i);
            if (k == '$') {
                if (pos != 0) {
                    return false;
                }
                continue;
            }
            if (pos == 0) {
                return false;
            }
            char c = w.charAt(pos - 1);
            if (k == '#') {
                if (!isVowelCh(c)) {
                    return false;
                }
            } else if (k == '^') {
                if (isVowelCh(c)) {
                    return false;
                }
            } else if (k != c) {
                return false;
            }
            pos--;
        }
        return true;
    }

    private static String wordToPhones(String word) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        int len = word.length();
        while (pos < len) {
            boolean hit = false;
            for (Rule r : RULES) {
                int mlen = r.match.length();
                if (mlen == 0 || pos + mlen > len) {
                    continue;
                }
                if (!word.regionMatches(pos, r.match, 0, mlen)) {
                    continue;
                }
                if (!ctxLeftOk(word, pos, r.left)) {
                    continue;
                }
                if (!ctxRightOk(word, pos + mlen, r.right)) {
                    continue;
                }
                if (!r.out.isEmpty()) {
                    if (out.length() > 0) {
                        out.append(' ');
                    }
                    out.append(r.out);
                }
                pos += mlen;
                hit = true;
                break;
            }
            if (!hit) {
                pos++;   // a letter no rule claims - digits, apostrophes
            }
        }
        return out.toString();
    }

    /** CH and JH are two sounds each; expand them once, here. */
    private static String expandAffricates(String in) {
        StringBuilder out = new StringBuilder();
        for (String tok : in.trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            String rep = switch (tok) {
                case "CH" -> "T SH";
                case "JH" -> "D ZH";
                default -> tok;
            };
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(rep);
        }
        return out.toString();
    }

    /** The phonemes a single word becomes. */
    public static String pronounce(String word) {
        return expandAffricates(wordToPhones(word.toLowerCase(Locale.ROOT)));
    }

    /** A word with its trailing punctuation taken off, and the silence that asks for. */
    private record Spoken(String word, int pauseMs) {
    }

    private static Spoken strip(String lowered) {
        String word = lowered;
        int pause = 0;
        while (!word.isEmpty() && ",.!?;:".indexOf(word.charAt(word.length() - 1)) >= 0) {
            pause = word.charAt(word.length() - 1) == ',' ? 110 : 220;
            word = word.substring(0, word.length() - 1);
        }
        return new Spoken(word, pause);
    }

    /** One word's phonemes, honouring a /HH EH R OW B R AY N/ spelling if it has one. */
    private static String phonesOfWord(String word) {
        if (word.length() > 2 && word.charAt(0) == '/' && word.endsWith("/")) {
            // phoneme names are upper case and the text was lowered on the way
            // in, so put the spelling back as written
            return expandAffricates(word.substring(1, word.length() - 1).toUpperCase(Locale.ROOT));
        }
        return pronounce(word);
    }

    /**
     * The phonemes a whole sentence becomes, read the way {@link #speak} reads
     * it: word by word, with the punctuation off. Running the letter rules over
     * a whole sentence instead would strand every '$' word-edge rule, so
     * "warning." would come out as W AA R N IH N G rather than W AO R N IH NG.
     */
    public static String phonemes(String text) {
        StringBuilder out = new StringBuilder();
        for (String raw : text.toLowerCase(Locale.ROOT).trim().split("[ \t]+")) {
            if (raw.isEmpty()) {
                continue;
            }
            String word = strip(raw).word();
            if (word.isEmpty()) {
                continue;
            }
            String p = phonesOfWord(word);
            if (!p.isEmpty()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(p);
            }
        }
        return out.toString();
    }

    // -------------------------------------------------------------- the tube

    /**
     * How long everything is held, against the phoneme table. An announcer
     * reading a prepared line is slower than a person talking, and the pauses
     * are what sell it as a public address.
     */
    private static final double SPEECH_RATE = 1.30;

    /**
     * Passes of the tube per output sample. Geometry, not quality: one pass
     * carries a wave across one cylinder, so cylinder length = c / (rate *
     * oversample) and the tract is NSEC of them. 5 x 22050 puts the tube at
     * 13.97 cm, a female tract, ringing near 626 / 1879 / 3132 Hz when uniform.
     */
    private static final int TRACT_OVERSAMPLE = 5;

    /**
     * How broad the tongue hump is, in cylinders. Swept from 4.2 down to 1.8
     * with no effect while hunting a missing F2, which ruled the tongue out; the
     * fault was in the measuring. Left at the value a tongue actually has.
     */
    private static final double TONGUE_SIGMA = 4.2;

    private static final int NSEC = 44;    // cylinders from glottis to lips: 13.97 cm at 5x
    private static final int NNOSE = 28;   // the nasal branch
    private static final int VELUM = 17;   // where the nose joins the throat

    /**
     * Wall absorption. Real tissue is soft and lossy, and that loss is what
     * gives a formant a width of 60-100 Hz instead of a spike. A lossless tube
     * rings like a pipe, which is what "too buzzy" sounds like.
     */
    private static final double WALL_LOSS = 0.9990;

    private static final class Tract {
        final double[] diam = new double[NSEC];
        final double[] area = new double[NSEC];
        final double[] r = new double[NSEC];
        final double[] l = new double[NSEC];
        final double[] noseArea = new double[NNOSE];
        final double[] nr = new double[NNOSE];
        final double[] nl = new double[NNOSE];
        double velumArea;

        Tract() {
            for (int i = 0; i < NSEC; i++) {
                diam[i] = 2.0;
                area[i] = 4.0;
            }
            // the nose is bone: it never changes shape, only whether it is connected
            for (int i = 0; i < NNOSE; i++) {
                double x = (double) i / (NNOSE - 1);
                double d = 1.9 * Math.sin(Math.PI * (0.15 + 0.85 * x)) + 0.4;
                noseArea[i] = d * d;
            }
        }
    }

    /** the shape a phoneme asks the tube to take */
    private static void shapeOf(Phone p, double ti, double td, double tighten, double[] out) {
        for (int i = 0; i < NSEC; i++) {
            // the tube at rest: narrow at the glottis, open through pharynx and mouth
            double base = (i < 7) ? 0.90 : (i < 13) ? 2.60 : 3.00;

            // the tongue: a broad hump that leaves the tube this wide where it sits
            double g = Math.exp(-((i - ti) * (i - ti)) / (2.0 * TONGUE_SIGMA * TONGUE_SIGMA));
            double d = base + (td - base) * g;

            // a consonant pinch on top of it, much narrower and much more local
            if (p.cd >= 0.0) {
                double h = Math.exp(-((i - p.ci) * (i - p.ci)) / (2.0 * 1.9 * 1.9));
                double cd = p.cd * tighten;
                d = d + (cd - d) * h;
            }
            if (i >= NSEC - 3) {
                d *= p.lip;
            }
            out[i] = d < 0.0 ? 0.0 : d;
        }
    }

    /**
     * one pass of waves through the tube: at every joint where the area changes,
     * a wave partly reflects and partly carries on.
     */
    private static double tractStep(Tract t, double glottalIn, double noiseIn, int noiseAt) {
        final double glottalRefl = 0.75, lipRefl = -0.85, noseRefl = -0.85;
        double[] newR = new double[NSEC];
        double[] newL = new double[NSEC];

        // turbulence is made AT the pinch, not added to the output
        if (noiseAt > 0 && noiseAt < NSEC) {
            t.r[noiseAt] += noiseIn;
        }

        newR[0] = t.l[0] * glottalRefl + glottalIn;

        for (int i = 0; i < NSEC - 1; i++) {
            if (i == VELUM) {
                continue;                 // the three-way, done below
            }
            double a1 = t.area[i], a2 = t.area[i + 1];
            double sum = a1 + a2;
            double k = sum > 1e-9 ? (a1 - a2) / sum : 1.0;
            double w = k * (t.r[i] + t.l[i + 1]);
            newR[i + 1] = t.r[i] - w;
            newL[i] = t.l[i + 1] + w;
        }

        /*
         * The velum: three tubes meeting. Each port reflects against the sum of
         * the other two. That form stays consistent with the two-way joints
         * above: set the nose area to zero and rl becomes exactly the k used
         * everywhere else, so a shut velum is silent by construction, no special
         * case. A pressure-balance form does not reduce that way and leaks a
         * fixed resonance into every vowel.
         */
        {
            double a1 = t.area[VELUM], a2 = t.area[VELUM + 1], an = t.velumArea;
            double sum = a1 + a2 + an;
            if (sum < 1e-9) {
                sum = 1e-9;
            }
            double rl = (a1 - a2 - an) / sum;
            double rr = (a2 - a1 - an) / sum;
            double rn = (an - a1 - a2) / sum;
            double inL = t.r[VELUM], inR = t.l[VELUM + 1], inN = t.nl[0];
            newL[VELUM] = rl * inL + (1.0 + rl) * (inN + inR);
            newR[VELUM + 1] = rr * inR + (1.0 + rr) * (inL + inN);
            t.nr[0] = rn * inN + (1.0 + rn) * (inR + inL);
        }

        newL[NSEC - 1] = t.r[NSEC - 1] * lipRefl;
        double out = t.r[NSEC - 1];

        for (int i = 0; i < NSEC; i++) {
            newR[i] *= WALL_LOSS;
            newL[i] *= WALL_LOSS;
        }
        System.arraycopy(newR, 0, t.r, 0, NSEC);
        System.arraycopy(newL, 0, t.l, 0, NSEC);

        // the nose, same arithmetic, rigid areas
        double[] nnR = new double[NNOSE];
        double[] nnL = new double[NNOSE];
        nnR[0] = t.nr[0];
        for (int i = 0; i < NNOSE - 1; i++) {
            double a1 = t.noseArea[i], a2 = t.noseArea[i + 1];
            double k = (a1 - a2) / (a1 + a2);
            double w = k * (t.nr[i] + t.nl[i + 1]);
            nnR[i + 1] = t.nr[i] - w;
            nnL[i] = t.nl[i + 1] + w;
        }
        nnL[NNOSE - 1] = t.nr[NNOSE - 1] * noseRefl;
        out += t.nr[NNOSE - 1];
        for (int i = 0; i < NNOSE; i++) {
            nnR[i] *= WALL_LOSS;
            nnL[i] *= WALL_LOSS;
        }
        System.arraycopy(nnR, 0, t.nr, 0, NNOSE);
        System.arraycopy(nnL, 0, t.nl, 0, NNOSE);

        return out;
    }

    // ----------------------------------------------------------- the glottis

    private static long rng = 0x2545F4914F6CDD1DL;

    private static double noiseSample() {
        rng ^= rng << 13;
        rng ^= rng >>> 7;
        rng ^= rng << 17;
        return (double) (rng >>> 11) / 9007199254740992.0 - 0.5;
    }

    /**
     * A body, at the few percent where a real one sits. Zero here reads as "not
     * alive" rather than "calm". The flatness that makes the announcer sound
     * like a machine is imposed a level up; see the class header.
     */
    private static final double JITTER = 0.004;    // pitch wander, pulse to pulse
    private static final double SHIMMER = 0.030;   // loudness wander, pulse to pulse
    private static final double BREATH = 0.026;    // air passing folds that never close completely

    /** one-pole coefficient for a ~1.5 kHz roll-off, applied twice: -12 dB/octave */
    private static final double TILT_K = 1.0 - 0.6417;

    /**
     * The closing edge of the fold. A real one closes fast but not instantly,
     * and the return is smooth; a hard edge gives a square wave. OPEN_Q sets how
     * much of the cycle the folds are apart, higher being breathier and softer.
     */
    private static double glottalPulse(double phase) {
        final double openQ = 0.62;
        if (phase < openQ) {
            // the opening: a raised cosine, so nothing has a corner in it
            double u = phase / openQ;
            double bell = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * u));
            // skewed late, the way a real cycle is
            return bell * (0.55 + 0.45 * u) - 0.30;
        }
        // the closed phase: quiet, with a soft return instead of a step
        double u = (phase - openQ) / (1.0 - openQ);
        return -0.30 * (1.0 - 0.25 * Math.exp(-u * 6.0));
    }

    // ------------------------------------------------------------ the utterance

    /** A growable run of samples. */
    private static final class Track {
        double[] buf = new double[16384];
        int n;

        void push(double v) {
            if (n == buf.length) {
                double[] bigger = new double[buf.length * 2];
                System.arraycopy(buf, 0, bigger, 0, n);
                buf = bigger;
            }
            buf[n++] = v;
        }
    }

    private static final class Voicebox {
        final Tract tr = new Tract();
        final double[] cur = new double[NSEC];   // the shape right now; glides toward each target
        double curVelum;
        double phase;
        double f0;
        double jitter;
        double shimmer;
        double vib;
        double tilt1;
        double tilt2;
        boolean started;
    }

    private static void renderPhone(Track t, Voicebox v, Phone p,
                                    double declination, double urgency) {
        final double rate = RATE;

        // urgency: shorter, tighter, higher
        double dur = p.dur * SPEECH_RATE * (1.0 - 0.22 * urgency);
        double tighten = 1.0 - 0.15 * urgency;
        int total = (int) (dur * rate / 1000.0);
        if (total == 0) {
            return;
        }

        int closure = p.kind == K_STOP ? (int) (total * 0.45) : 0;
        int burst = (int) (rate * 0.010);

        double[] target = new double[NSEC];
        for (int i = 0; i < total; i++) {
            double u = (double) i / (double) total;

            // diphthongs move the tongue across the sound; the tube follows
            double ti = p.ti, td = p.td;
            if (p.gi > 0.0) {
                double g = u < 0.35 ? 0.0 : (u - 0.35) / 0.65;
                ti = p.ti + (p.gi - p.ti) * g;
                td = p.td + (p.gd - p.td) * g;
            }
            shapeOf(p, ti, td, tighten, target);

            // the tract cannot teleport: about 22 ms to reach a new shape
            double k = 1.0 - Math.exp(-1.0 / (0.022 * rate));
            if (!v.started) {
                System.arraycopy(target, 0, v.cur, 0, NSEC);
                v.started = true;
            }
            for (int s = 0; s < NSEC; s++) {
                v.cur[s] += (target[s] - v.cur[s]) * k;
            }
            v.curVelum += (p.velum - v.curVelum) * k;

            for (int s = 0; s < NSEC; s++) {
                v.tr.diam[s] = v.cur[s];
                v.tr.area[s] = v.cur[s] * v.cur[s];
            }
            v.tr.velumArea = v.curVelum * v.curVelum * 2.2;

            // the source
            double src = 0.0;
            if (p.voiced && !(p.kind == K_STOP && i < closure)) {
                double f0 = v.f0 * declination * (1.0 + 0.24 * urgency);
                v.vib += 4.7 / rate;                       // the slow one, ~4.7 Hz
                if (v.vib >= 1.0) {
                    v.vib -= 1.0;
                }
                f0 *= 1.0 + 0.004 * Math.sin(2.0 * Math.PI * v.vib);
                f0 *= 1.0 + v.jitter;
                v.phase += f0 / rate;
                if (v.phase >= 1.0) {
                    // a new cycle: the folds never repeat themselves exactly
                    v.phase -= 1.0;
                    v.jitter = noiseSample() * 2.0 * JITTER;
                    v.shimmer = 1.0 + noiseSample() * 2.0 * SHIMMER;
                }

                src = glottalPulse(v.phase) * 0.85 * v.shimmer;

                /*
                 * Spectral tilt. A real glottal wave falls away at about 12 dB
                 * per octave, so almost nothing of it survives above 2 kHz; the
                 * highs in a voice come from the tract, not the folds. An
                 * untilted pulse hands the tube a bright edge no throat produces.
                 */
                v.tilt1 += (src - v.tilt1) * TILT_K;
                v.tilt2 += (v.tilt1 - v.tilt2) * TILT_K;
                src = v.tilt2 * 2.4;

                // air that gets past folds which never fully close
                src += noiseSample() * BREATH;
            }

            double nz = 0.0;
            int nzAt = -1;
            if (p.noise > 0.0) {
                double lvl = p.noise;
                if (p.kind == K_STOP) {
                    if (i < closure) {
                        lvl = 0.0;
                    } else {
                        int since = i - closure;
                        lvl *= since < burst ? 1.0
                                : Math.exp(-(double) (since - burst) / (0.012 * rate));
                    }
                }
                nz = noiseSample() * lvl * 0.5 * (1.0 + 0.35 * urgency);
                nzAt = (int) (p.ci > 0 ? p.ci : 30);
            }

            double env = 1.0;
            double edge = 0.005 * rate;
            if (i < edge) {
                env = i / edge;
            }
            if (total - i < edge) {
                env = (total - i) / edge;
            }

            /*
             * TRACT_OVERSAMPLE passes per output sample. Not a quality knob: it
             * sets the physical length of the tube, since one pass moves a wave
             * one cylinder and the tract is NSEC of them. Halve it and the tube
             * doubles in length, the resonances halve, and every vowel collapses
             * toward the same hum. Measured at 2x: F1 sat at 250 Hz for AA and
             * IY alike.
             */
            double y = 0.0;
            for (int sub = 0; sub < TRACT_OVERSAMPLE; sub++) {
                y += tractStep(v.tr, src * p.amp * env, nz * env, nzAt);
            }
            t.push(y / TRACT_OVERSAMPLE);
        }
    }

    // ------------------------------------------------------ the ceiling speaker

    private static void paChain(Track t, double urgency) {
        final double rate = RATE;

        // the band a speaker passes. the missing bottom is most of what makes it
        // read as a tannoy and not a man in the room.
        double hx1 = 0, hy1 = 0, lp1 = 0, lp2 = 0;
        final double hc = Math.exp(-2.0 * Math.PI * 320.0 / rate);
        final double la = 1.0 - Math.exp(-2.0 * Math.PI * 3700.0 / rate);
        for (int i = 0; i < t.n; i++) {
            double x = t.buf[i];
            double hy = hc * (hy1 + x - hx1);
            hx1 = x;
            hy1 = hy;
            lp1 += la * (hy - lp1);
            lp2 += la * (lp1 - lp2);
            t.buf[i] = lp2 * 2.2;
        }

        // driven a little past polite, the way an announcement system is
        double drive = 1.12 + 0.70 * urgency;
        for (int i = 0; i < t.n; i++) {
            t.buf[i] = Math.tanh(t.buf[i] * drive) / Math.tanh(drive);
        }

        // one hard ceiling and one far wall
        int d1 = (int) (0.041 * rate), d2 = (int) (0.073 * rate);
        for (int i = d1; i < t.n; i++) {
            t.buf[i] += 0.15 * t.buf[i - d1];
        }
        for (int i = d2; i < t.n; i++) {
            t.buf[i] += 0.07 * t.buf[i - d2];
        }

        double peak = 0.0;
        for (int i = 0; i < t.n; i++) {
            peak = Math.max(peak, Math.abs(t.buf[i]));
        }
        if (peak > 0) {
            for (int i = 0; i < t.n; i++) {
                t.buf[i] *= 0.82 / peak;
            }
        }
    }

    /** the channel opening and closing */
    private static void keyClick(Track t, boolean opening) {
        int n = (int) (0.014 * RATE);
        double y1 = 0, y2 = 0;
        double r = Math.exp(-Math.PI * 1500.0 / RATE);
        double b = 2.0 * r * Math.cos(2.0 * Math.PI * (opening ? 2300.0 : 850.0) / RATE);
        double c = -r * r, a = 1.0 - b - c;
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-(double) i / (0.0022 * RATE));
            double y = a * noiseSample() + b * y1 + c * y2;
            y2 = y1;
            y1 = y;
            t.push(y * env * (opening ? 1.3 : 0.85));
        }
    }

    // ================================================================== the eras

    public static final int ERA_1950 = 1950;   // Haskins pattern playback
    public static final int ERA_1962 = 1962;   // Kelly-Lochbaum tube. the one above.
    public static final int ERA_1980 = 1980;   // Votrax-class phoneme chip
    public static final int ERA_1990 = 1990;   // Klatt cascade: source and filter

    /**
     * 1980 is the announcer, judged by ear. The two engines that sound like a
     * facility are the two with a budget, 1950 and 1980; a machine straining to
     * be understood is the unnerving one. The smooth pair are kept but kept out
     * of the corridor.
     */
    public static final int DEFAULT_ERA = ERA_1980;

    public static boolean eraKnown(int year) {
        return year == ERA_1950 || year == ERA_1962 || year == ERA_1980 || year == ERA_1990;
    }

    /**
     * Formant targets: how every engine except the tube sees a phoneme. Adult
     * female, the announcer being one. F3 matters less than the first two, and
     * 1950 does not have it at all. Each era is allowed exactly what it had.
     */
    private record Formant(String name, double f1, double f2, double f3,
                           double g1, double g2, double g3) {
    }

    private static final Formant[] FORMANTS = {
            new Formant("IY", 310, 2790, 3310, 0, 0, 0),
            new Formant("IH", 430, 2480, 3070, 0, 0, 0),
            new Formant("EH", 610, 2330, 2990, 0, 0, 0),
            new Formant("AE", 860, 2050, 2850, 0, 0, 0),
            new Formant("AA", 850, 1220, 2810, 0, 0, 0),
            new Formant("AO", 590, 920, 2710, 0, 0, 0),
            new Formant("AH", 760, 1400, 2780, 0, 0, 0),
            new Formant("UH", 470, 1160, 2680, 0, 0, 0),
            new Formant("UW", 370, 950, 2670, 0, 0, 0),
            new Formant("ER", 500, 1640, 1960, 0, 0, 0),
            new Formant("AY", 850, 1220, 2810, 310, 2790, 3310),
            new Formant("EY", 610, 2330, 2990, 310, 2790, 3310),
            new Formant("OW", 590, 920, 2710, 370, 950, 2670),
            new Formant("AW", 850, 1220, 2810, 370, 950, 2670),
            new Formant("OY", 590, 920, 2710, 310, 2790, 3310),
            new Formant("M", 280, 900, 2200, 0, 0, 0),
            new Formant("N", 280, 1700, 2600, 0, 0, 0),
            new Formant("NG", 280, 2300, 2750, 0, 0, 0),
            new Formant("L", 380, 880, 2575, 0, 0, 0),
            new Formant("R", 420, 1300, 1600, 0, 0, 0),
            new Formant("W", 330, 700, 2200, 0, 0, 0),
            new Formant("Y", 300, 2400, 3070, 0, 0, 0),
            new Formant("F", 400, 1300, 2200, 0, 0, 0),
            new Formant("TH", 400, 1600, 2200, 0, 0, 0),
            new Formant("S", 400, 1600, 2500, 0, 0, 0),
            new Formant("SH", 400, 2000, 2400, 0, 0, 0),
            new Formant("HH", 500, 1600, 2500, 0, 0, 0),
            new Formant("V", 400, 1300, 2200, 0, 0, 0),
            new Formant("DH", 400, 1600, 2200, 0, 0, 0),
            new Formant("Z", 400, 1600, 2500, 0, 0, 0),
            new Formant("ZH", 400, 2000, 2400, 0, 0, 0),
            new Formant("P", 400, 1100, 2200, 0, 0, 0),
            new Formant("T", 400, 1800, 2600, 0, 0, 0),
            new Formant("K", 400, 2000, 2400, 0, 0, 0),
            new Formant("B", 300, 1100, 2200, 0, 0, 0),
            new Formant("D", 300, 1800, 2600, 0, 0, 0),
            new Formant("G", 300, 2000, 2400, 0, 0, 0),
            new Formant("_", 0, 0, 0, 0, 0, 0),
    };

    private static Formant formantFor(String name) {
        for (Formant f : FORMANTS) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    /** noise band centres, for the eras that can make frication at all */
    private static double fricationHz(String n) {
        return switch (n) {
            case "S", "Z" -> 6000;
            case "SH", "ZH" -> 2600;
            case "F", "V" -> 4500;
            case "TH", "DH" -> 5200;
            case "T", "D" -> 3800;
            case "K", "G" -> 2200;
            case "P", "B" -> 1200;
            default -> 1800;
        };
    }

    /** one sound, as the pre-tube engines see it */
    private static final class Seg {
        Formant form;
        double ms;
        double amp;
        boolean voiced;
        boolean noisy;         // frication or a burst somewhere in it
        boolean burst;         // a stop: the noise is an event, not a texture
        boolean lastOfClause;  // the only prosody: a sag at the end
    }

    /**
     * A two-pole resonator: the arithmetic under every formant synthesiser here.
     * Feed it a buzz and it rings at one frequency, as a cavity does.
     */
    private static final class Reson {
        double a, b, c, y1, y2;

        void set(double freq, double bw, double rate) {
            double r = Math.exp(-Math.PI * bw / rate);
            double theta = 2.0 * Math.PI * freq / rate;
            c = -r * r;
            b = 2.0 * r * Math.cos(theta);
            a = 1.0 - b - c;
        }

        double run(double x) {
            double y = a * x + b * y1 + c * y2;
            y2 = y1;
            y1 = y;
            return y;
        }
    }

    private static long erRng = 0x9E3779B97F4A7C15L;

    private static double erNoise() {
        erRng ^= erRng << 13;
        erRng ^= erRng >>> 7;
        erRng ^= erRng << 17;
        return (double) (erRng >>> 11) / 9007199254740992.0 - 0.5;
    }

    /**
     * Pattern playback. Two bands of resonance, a fifty-cycle buzz, and nothing
     * else: no third formant, no real frication, no transition a brush could not
     * paint. Fricatives were painted as smears and read as hisses.
     *
     * The bands step instead of gliding, because a hand-painted spectrogram is a
     * series of strokes and the machine reads whatever is under the light. It
     * should come out just barely intelligible.
     */
    private static void render1950(List<Seg> segs, double rate, Track out) {
        Reson r1 = new Reson(), r2 = new Reson();
        double phase = 0.0;

        for (Seg s : segs) {
            Formant f = s.form;
            int n = (int) (s.ms * rate / 1000.0);
            for (int i = 0; i < n; i++) {
                if (f == null || f.f1 <= 0) {
                    out.push(0.0);
                    continue;
                }
                double u = (double) i / (double) n;

                // a brush stroke: the band may move within a sound, but in steps
                double q = Math.floor(u * 4.0) / 4.0;
                double f1 = f.f1 + (f.g1 != 0 ? (f.g1 - f.f1) * q : 0);
                double f2 = f.f2 + (f.g2 != 0 ? (f.g2 - f.f2) * q : 0);

                // fifty cycles; the film has no idea what a fold is
                phase += 50.0 / rate;
                if (phase >= 1.0) {
                    phase -= 1.0;
                }
                double src = phase < 0.06 ? 1.0 : -0.06;
                if (s.noisy) {
                    src = erNoise() * 2.2;
                }

                r1.set(f1, 130, rate);
                r2.set(f2, 190, rate);
                double y = r1.run(src) * 1.0 + r2.run(src) * 0.6;

                double env = 1.0, edge = 0.004 * rate;
                if (i < edge) {
                    env = i / edge;
                }
                if (n - i < edge) {
                    env = (n - i) / edge;
                }
                out.push(y * 0.55 * env);
            }
        }
    }

    /**
     * The phoneme chip. Three formants, a hard buzz, and everything quantised
     * because bits cost money in 1980: pitch on a coarse grid, sixteen steps of
     * amplitude, the whole thing running at 8 kHz and then held, not
     * interpolated, back up to the output rate. Transitions are a single step at
     * the phoneme boundary. Every limit here is a budget, not an accident of the
     * model.
     */
    private static void render1980(List<Seg> segs, double rate, Track out) {
        Reson r1 = new Reson(), r2 = new Reson(), r3 = new Reson(), nz = new Reson();
        double phase = 0.0;
        final double chipRate = 8000.0;
        double hold = 0.0, heldFor = 0.0;

        for (Seg s : segs) {
            Formant f = s.form;
            int n = (int) (s.ms * rate / 1000.0);
            for (int i = 0; i < n; i++) {
                if (f == null || f.f1 <= 0) {
                    out.push(0.0);
                    continue;
                }

                // the chip only computes 8000 times a second; between those, hold
                heldFor += chipRate / rate;
                if (heldFor >= 1.0) {
                    heldFor -= 1.0;

                    // pitch on a coarse grid; there are not many bits for it
                    double f0 = Math.floor(122.0 / 4.0) * 4.0;
                    phase += f0 / chipRate;
                    if (phase >= 1.0) {
                        phase -= 1.0;
                    }
                    double src = phase < 0.5 ? 0.9 : -0.9;      // a square wave
                    if (s.noisy) {
                        nz.set(fricationHz(f.name), 1500, chipRate);
                        src = nz.run(erNoise()) * 5.0;
                    }

                    r1.set(f.f1, 90, chipRate);
                    r2.set(f.f2, 110, chipRate);
                    r3.set(f.f3 > 0 ? f.f3 : 2500, 180, chipRate);
                    double y = r3.run(r2.run(r1.run(src)));

                    // sixteen steps of loudness
                    hold = Math.floor(y * 8.0 + 0.5) / 8.0;
                }
                out.push(hold * 0.5);
            }
        }
    }

    /**
     * Klatt cascade, the one 1998 would have reached for. Five resonators in
     * series, a glottal source with a real spectral tilt, aspiration mixed at
     * the glottis, frication injected after the cascade instead of through it,
     * and formants that glide from one target to the next because a mouth cannot
     * jump.
     *
     * What separates this from 1980 is not more computer. It is knowing which
     * details carry intelligibility.
     */
    private static void render1990(List<Seg> segs, double rate, Track out) {
        Reson r1 = new Reson(), r2 = new Reson(), r3 = new Reson();
        Reson r4 = new Reson(), r5 = new Reson(), nzr = new Reson();
        double phase = 0.0, tilt1 = 0.0, tilt2 = 0.0;
        double c1 = 0, c2 = 0, c3 = 0;          // where the formants are right now
        double jitter = 0.0, vib = 0.0;

        for (Seg s : segs) {
            Formant f = s.form;
            int n = (int) (s.ms * rate / 1000.0);
            if (f == null || f.f1 <= 0) {
                for (int i = 0; i < n; i++) {
                    out.push(0.0);
                }
                continue;
            }
            if (c1 == 0) {
                c1 = f.f1;
                c2 = f.f2;
                c3 = f.f3;
            }

            for (int i = 0; i < n; i++) {
                double u = (double) i / (double) n;

                double t1 = f.f1, t2 = f.f2, t3 = f.f3;
                if (f.g1 > 0) {           // a diphthong moves while it is held
                    double g = u < 0.35 ? 0.0 : (u - 0.35) / 0.65;
                    t1 += (f.g1 - t1) * g;
                    t2 += (f.g2 - t2) * g;
                    t3 += (f.g3 - t3) * g;
                }
                // glide toward the target: a mouth takes about 25 ms to get there
                double k = 1.0 - Math.exp(-1.0 / (0.025 * rate));
                c1 += (t1 - c1) * k;
                c2 += (t2 - c2) * k;
                c3 += (t3 - c3) * k;

                // the source: a body, at the few percent where a real one sits
                vib += 4.7 / rate;
                if (vib >= 1.0) {
                    vib -= 1.0;
                }
                double f0 = 186.0 * (1.0 + 0.004 * Math.sin(2.0 * Math.PI * vib)) * (1.0 + jitter);
                f0 *= 1.0 - 0.06 * u * (s.lastOfClause ? 1.0 : 0.0);
                phase += f0 / rate;
                if (phase >= 1.0) {
                    phase -= 1.0;
                    jitter = erNoise() * 0.008;
                }

                // Klatt's glottal wave: smooth rise, faster fall, no corners
                double g = phase < 0.62
                        ? 0.5 * (1.0 - Math.cos(2.0 * Math.PI * phase / 0.62))
                        * (0.55 + 0.45 * phase / 0.62)
                        : 0.0;
                double src = g - 0.28;

                // -12 dB/octave, which is what a real glottis gives the tract
                tilt1 += (src - tilt1) * 0.36;
                tilt2 += (tilt1 - tilt2) * 0.36;
                src = tilt2 * 2.6;
                src += erNoise() * 0.022;                 // aspiration at the folds

                if (!s.voiced) {
                    src = erNoise() * 0.30;
                }

                r1.set(c1, 70, rate);
                r2.set(c2, 100, rate);
                r3.set(c3 > 0 ? c3 : 2600, 160, rate);
                r4.set(3400, 220, rate);
                r5.set(4200, 260, rate);
                double y = r5.run(r4.run(r3.run(r2.run(r1.run(src)))));

                // frication is made in front of the constriction, so it does not
                // go through the cascade. Klatt put it in parallel for that reason.
                if (s.noisy) {
                    nzr.set(fricationHz(f.name), 900, rate);
                    double h = nzr.run(erNoise()) * 5.0;
                    double lvl = s.burst ? (u < 0.25 ? 1.0 : Math.exp(-(u - 0.25) * 16.0)) : 1.0;
                    y += h * 0.5 * lvl;
                }

                double env = 1.0, edge = 0.006 * rate;
                if (i < edge) {
                    env = i / edge;
                }
                if (n - i < edge) {
                    env = (n - i) / edge;
                }
                out.push(y * s.amp * env);
            }
        }
    }

    /**
     * Hand the same phoneme sequence to an older engine. The letters, the rules
     * and the sentence are untouched; only the engine differs.
     */
    private static void renderEra(Track t, List<Phone> seq, List<Double> ms,
                                  double urgency, int era) {
        List<Seg> segs = new ArrayList<>(seq.size());
        for (int i = 0; i < seq.size(); i++) {
            Seg s = new Seg();
            Phone p = seq.get(i);
            s.form = formantFor(p.name);
            s.ms = ms.get(i) * (1.0 - 0.22 * urgency);
            s.amp = p.amp;
            s.voiced = p.voiced;
            s.noisy = p.noise > 0.0;
            s.burst = p.kind == K_STOP;
            s.lastOfClause = (i + 1 == seq.size()) || (seq.get(i + 1).kind == K_SIL);
            segs.add(s);
        }
        switch (era) {
            case ERA_1950 -> render1950(segs, RATE, t);
            case ERA_1980 -> render1980(segs, RATE, t);
            default -> render1990(segs, RATE, t);
        }
    }

    // =============================================================== speaking

    /**
     * Speak text into 16-bit mono PCM at {@link #RATE}.
     *
     * text is ordinary words ("attention. intruder in sector c.") turned into
     * sounds by the letter rules above. those rules are approximate. an exact
     * pronunciation can be spelled between slashes instead:
     *
     *     /HH EH R OW B R AY N/
     *
     * urgency runs 0..1 and modulates the geometry, not the mix: the tract
     * shortens and tightens, the pitch lifts, the delivery quickens.
     *
     * @return the samples, or an empty array if there was nothing sayable
     */
    public static short[] speak(String text, double urgency, int era) {
        urgency = Math.clamp(urgency, 0.0, 1.0);
        if (!eraKnown(era)) {
            era = DEFAULT_ERA;
        }

        Track t = new Track();
        Voicebox v = new Voicebox();
        /*
         * The announcer is a woman: 186 Hz, and the tube runs a pass faster so it
         * is 13.97 cm instead of 17.4. A shorter throat lifts every resonance
         * about a quarter. Geometry, not a pitch shift laid over a man.
         */
        v.f0 = 186.0;
        v.shimmer = 1.0;

        keyClick(t, true);

        boolean said = false;
        int spoken = 0;

        // the older engines want the whole sequence before they start
        List<Phone> seq = new ArrayList<>();
        List<Double> seqMs = new ArrayList<>();

        for (String rawWord : text.toLowerCase(Locale.ROOT).trim().split("[ \t]+")) {
            String word = rawWord;
            if (word.isEmpty()) {
                continue;
            }

            // a sentence sags as it runs, then resets
            double declination = 1.0 - 0.09 * (double) (spoken % 8) / 8.0;

            Spoken s = strip(word);
            word = s.word();
            int pauseMs = s.pauseMs();
            if (word.isEmpty()) {
                continue;
            }

            String phones = phonesOfWord(word);

            for (String tok : phones.trim().split("\\s+")) {
                if (tok.isEmpty()) {
                    continue;
                }
                Phone ph = phoneNamed(tok);
                if (ph == null) {
                    continue;
                }
                if (era == ERA_1962) {
                    renderPhone(t, v, ph, declination, urgency);
                } else {
                    seq.add(ph);
                    seqMs.add(ph.dur * SPEECH_RATE);
                }
                said = true;
            }
            spoken++;

            Phone sil = phoneNamed("_");
            double gapMs = (pauseMs != 0 ? pauseMs * 1.25 : 90) * (1.0 - 0.35 * urgency);
            if (era == ERA_1962) {
                Phone gap = new Phone(sil.name, sil.kind, sil.ti, sil.td, sil.ci, sil.cd,
                        sil.lip, sil.velum, sil.gi, sil.gd, gapMs, sil.amp, sil.voiced, sil.noise);
                renderPhone(t, v, gap, 1.0, urgency);
            } else {
                seq.add(sil);
                seqMs.add(gapMs);
            }
        }

        if (!said) {
            return new short[0];
        }

        if (era != ERA_1962) {
            renderEra(t, seq, seqMs, urgency, era);
        }

        keyClick(t, false);
        paChain(t, urgency);

        short[] pcm = new short[t.n];
        for (int i = 0; i < t.n; i++) {
            double s = t.buf[i] * 32767.0;
            pcm[i] = (short) Math.clamp(s, -32768, 32767);
        }
        return pcm;
    }

    // -------------------------------------------------------------------- wav

    /** Wrap PCM as a .wav file in memory. */
    public static byte[] wav(short[] pcm) {
        int data = pcm.length * 2;
        byte[] b = new byte[44 + data];
        putAscii(b, 0, "RIFF");
        put32(b, 4, 36 + data);
        putAscii(b, 8, "WAVEfmt ");
        put32(b, 16, 16);
        put16(b, 20, 1);
        put16(b, 22, 1);
        put32(b, 24, RATE);
        put32(b, 28, RATE * 2);
        put16(b, 32, 2);
        put16(b, 34, 16);
        putAscii(b, 36, "data");
        put32(b, 40, data);
        for (int i = 0; i < pcm.length; i++) {
            put16(b, 44 + i * 2, pcm[i]);
        }
        return b;
    }

    private static void putAscii(byte[] b, int at, String s) {
        byte[] raw = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, b, at, raw.length);
    }

    private static void put32(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
        b[at + 2] = (byte) (v >> 16);
        b[at + 3] = (byte) (v >> 24);
    }

    private static void put16(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }

    // ================================================================ the haunts

    /** One haunt: when it may fire, how likely it is, and what happens if it does. */
    public static final class Haunt {
        final String name;
        final int line;
        int afterSecs;
        String afterName = "";
        boolean once;
        boolean urgent;                 // spoken tighter and higher
        int era;                        // 0 = whatever the world says
        int chanceNum = 1;
        int chanceDen = 1;
        String where = "";
        final List<String> vox = new ArrayList<>();
        final List<String> act = new ArrayList<>();
        final List<String> say = new ArrayList<>();

        // director state
        boolean fired;
        long base;                      // earliest second it may be considered from
        boolean blocked;                // waiting on another haunt to fire first

        Haunt(String name, int line) {
            this.name = name;
            this.line = line;
        }

        public String name() {
            return name;
        }

        public boolean urgent() {
            return urgent;
        }

        public int era() {
            return era;
        }

        public List<String> voxLines() {
            return List.copyOf(vox);
        }
    }

    /** A parsed night: its name, its seed, its default engine and its haunts. */
    public static final class World {
        String name = "unnamed";
        int era = DEFAULT_ERA;
        long seed = 1;
        final List<Haunt> haunts = new ArrayList<>();

        Haunt named(String n) {
            for (Haunt h : haunts) {
                if (h.name.equals(n)) {
                    return h;
                }
            }
            return null;
        }

        public String worldName() {
            return name;
        }

        public int worldEra() {
            return era;
        }

        public long seed() {
            return seed;
        }

        public List<Haunt> haunts() {
            return List.copyOf(haunts);
        }
    }

    /** A complaint about the source, with the line it happened on. */
    public static final class HauntSyntaxException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        HauntSyntaxException(int line, String msg, String detail) {
            super(line > 0
                    ? "line " + line + ": " + msg + (detail != null ? " '" + detail + "'" : "")
                    : msg + (detail != null ? " '" + detail + "'" : ""));
        }
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String join(String[] w, int from, int to) {
        return String.join(" ", java.util.Arrays.copyOfRange(w, from, to));
    }

    /**
     * Read a world. Seed and era can be overridden from outside, in which case
     * the file's own values are read and ignored, the way the flags did.
     */
    public static World parse(String src, Long seedOverride, Integer eraOverride) {
        World world = new World();
        if (seedOverride != null) {
            world.seed = seedOverride;
        }
        if (eraOverride != null) {
            world.era = eraOverride;
        }

        Haunt cur = null;
        int line = 0;

        for (String rawLine : src.split("\n", -1)) {
            line++;
            String stripped = rawLine;
            int hash = stripped.indexOf('#');
            if (hash >= 0) {
                stripped = stripped.substring(0, hash);
            }
            stripped = stripped.replace('\r', ' ').trim();
            if (stripped.isEmpty()) {
                continue;
            }

            String[] w = stripped.split("[ \t]+");
            int n = w.length;
            String verb = w[0];

            if (verb.equals("world")) {
                if (n < 2) {
                    throw new HauntSyntaxException(line, "world needs a name", null);
                }
                world.name = join(w, 1, n);
                cur = null;
            } else if (verb.equals("seed")) {
                if (n != 2 || !isNumber(w[1])) {
                    throw new HauntSyntaxException(line, "seed needs one number", null);
                }
                if (seedOverride == null) {
                    world.seed = Long.parseUnsignedLong(w[1]);
                }
                cur = null;
            } else if (verb.equals("era") && cur == null) {
                // the machine the facility speaks through, unless a haunt overrides it
                if (n != 2 || !isNumber(w[1])) {
                    throw new HauntSyntaxException(line, "era wants a year", null);
                }
                int y = Integer.parseInt(w[1]);
                if (!eraKnown(y)) {
                    throw new HauntSyntaxException(line,
                            "no engine from that year. try 1950, 1962, 1980 or 1990.", w[1]);
                }
                if (eraOverride == null) {
                    world.era = y;
                }
            } else if (verb.equals("haunt")) {
                if (n != 2) {
                    throw new HauntSyntaxException(line, "haunt needs one name", null);
                }
                if (world.named(w[1]) != null) {
                    throw new HauntSyntaxException(line, "haunt already named", w[1]);
                }
                cur = new Haunt(w[1], line);
                world.haunts.add(cur);
            } else if (cur == null) {
                throw new HauntSyntaxException(line, "statement outside a haunt", verb);
            } else if (verb.equals("after")) {
                if (n == 3 && isNumber(w[1]) && (w[2].equals("seconds") || w[2].equals("second"))) {
                    cur.afterSecs = Integer.parseInt(w[1]);
                } else if (n == 2) {
                    cur.afterName = w[1];
                } else {
                    throw new HauntSyntaxException(line,
                            "after wants <n> seconds, or a haunt name", null);
                }
            } else if (verb.equals("chance")) {
                if (n != 4 || !isNumber(w[1]) || !w[2].equals("in") || !isNumber(w[3])) {
                    throw new HauntSyntaxException(line, "chance wants <n> in <m>", null);
                }
                cur.chanceNum = Integer.parseInt(w[1]);
                cur.chanceDen = Integer.parseInt(w[3]);
                if (cur.chanceDen <= 0) {
                    throw new HauntSyntaxException(line, "chance in zero", null);
                }
                if (cur.chanceNum > cur.chanceDen) {
                    throw new HauntSyntaxException(line, "chance greater than certain", null);
                }
            } else if (verb.equals("era")) {
                // which machine speaks this haunt, overriding the world's
                if (n != 2 || !isNumber(w[1])) {
                    throw new HauntSyntaxException(line, "era wants a year", null);
                }
                int y = Integer.parseInt(w[1]);
                if (!eraKnown(y)) {
                    throw new HauntSyntaxException(line,
                            "no engine from that year. try 1950, 1962, 1980 or 1990.", w[1]);
                }
                cur.era = y;
            } else if (verb.equals("urgent")) {
                if (n != 1) {
                    throw new HauntSyntaxException(line, "urgent takes nothing", null);
                }
                cur.urgent = true;
            } else if (verb.equals("once")) {
                if (n != 1) {
                    throw new HauntSyntaxException(line, "once takes nothing", null);
                }
                cur.once = true;
            } else if (verb.equals("where")) {
                if (n < 2) {
                    throw new HauntSyntaxException(line, "where needs a place", null);
                }
                cur.where = join(w, 1, n);
            } else if (verb.equals("vox")) {
                if (n < 2) {
                    throw new HauntSyntaxException(line, "vox needs something to say", null);
                }
                cur.vox.add(join(w, 1, n));
            } else if (verb.equals("do")) {
                if (n < 2) {
                    throw new HauntSyntaxException(line, "do needs an action", null);
                }
                cur.act.add(join(w, 1, n));
            } else if (verb.equals("say")) {
                if (n < 2) {
                    throw new HauntSyntaxException(line, "say needs words", null);
                }
                cur.say.add(join(w, 1, n));
            } else {
                throw new HauntSyntaxException(line, "no such verb", verb);
            }
        }

        // every `after <haunt>` must name a haunt that exists
        for (Haunt h : world.haunts) {
            if (h.afterName.isEmpty()) {
                continue;
            }
            if (h.afterName.equals(h.name)) {
                throw new HauntSyntaxException(h.line, "haunt waits on itself", h.name);
            }
            if (world.named(h.afterName) == null) {
                throw new HauntSyntaxException(h.line, "after names no such haunt", h.afterName);
            }
        }

        /*
         * a haunt that may fire more than once has to say how often. without a
         * number it would re-roll every tick and fire in a heap. so the pacing is
         * stated, or the haunt is `once`.
         */
        for (Haunt h : world.haunts) {
            if (!h.once && h.afterSecs == 0) {
                throw new HauntSyntaxException(h.line,
                        "a haunt that repeats needs 'after <n> seconds' to pace it, or 'once'",
                        h.name);
            }
        }

        return world;
    }

    // ==================================================================== chance

    private static long rngState = 1;

    private static void rngSeed(long s) {
        rngState = s != 0 ? s : 0x9E3779B97F4A7C15L;
    }

    private static long rngNext() {
        long x = rngState;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        rngState = x;
        return x;
    }

    private static boolean rollsTrue(int num, int den) {
        if (num <= 0) {
            return false;
        }
        if (num >= den) {
            return true;
        }
        return Long.remainderUnsigned(rngNext(), den) < num;
    }

    // ==================================================================== the night

    /** What a fired haunt looks like to whatever is watching. */
    public interface Witness {
        void fired(long at, World world, Haunt haunt);
    }

    /** The default witness: one readable block on stdout. */
    public static final Witness STDOUT = (at, world, haunt) -> {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%5ds] %s", at, haunt.name));
        if (!haunt.where.isEmpty()) {
            sb.append("  in ").append(haunt.where);
        }
        System.out.println(sb);
        for (String v : haunt.vox) {
            System.out.println("          vox " + v);
        }
        for (String s : haunt.say) {
            System.out.println("          say " + s);
        }
        for (String a : haunt.act) {
            System.out.println("          do  " + a);
        }
        System.out.flush();
    };

    private static void fire(World world, Haunt h, long t, Witness witness) {
        witness.fired(t, world, h);
        h.fired = true;
        if (!h.once) {
            h.base = t + h.afterSecs;   // paced, per the load-time rule
        }
        for (Haunt other : world.haunts) {
            if (other.afterName.equals(h.name) && other.blocked) {
                other.blocked = false;
                other.base = t + other.afterSecs;
            }
        }
    }

    /**
     * Run the night. seconds < 0 runs until interrupted; tickMs 0 runs the whole
     * night as fast as it can be computed.
     */
    public static void runNight(World world, long seconds, int tickMs, Witness witness)
            throws InterruptedException {
        for (Haunt h : world.haunts) {
            h.fired = false;
            h.blocked = !h.afterName.isEmpty();
            h.base = h.afterName.isEmpty() ? h.afterSecs : 0;
        }
        rngSeed(world.seed);

        for (long t = 0; seconds < 0 || t <= seconds; t++) {
            for (Haunt h : world.haunts) {
                if (h.blocked) {
                    continue;
                }
                if (h.once && h.fired) {
                    continue;
                }
                if (t < h.base) {
                    continue;
                }
                if (rollsTrue(h.chanceNum, h.chanceDen)) {
                    fire(world, h, t, witness);
                }
            }
            if (tickMs > 0) {
                Thread.sleep(tickMs);
            }
        }
    }

    // =================================================================== the checks

    private static void printCheck(World world, String path) {
        System.out.printf("world   %s%n", world.name);
        System.out.printf("seed    %s%n", Long.toUnsignedString(world.seed));
        System.out.printf("source  %s%n", path);
        System.out.printf("era     %d, unless a haunt says otherwise%n", world.era);
        System.out.printf("haunts  %d%n%n", world.haunts.size());

        for (Haunt h : world.haunts) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  %-14s", h.name));
            if (!h.afterName.isEmpty()) {
                sb.append("after ").append(h.afterName);
                if (h.afterSecs != 0) {
                    sb.append(" +").append(h.afterSecs).append("s");
                }
            } else {
                sb.append("after ").append(h.afterSecs).append("s");
            }
            sb.append(", ").append(h.chanceNum).append(" in ").append(h.chanceDen);
            if (h.once) {
                sb.append(", once");
            }
            if (h.urgent) {
                sb.append(", urgent");
            }
            if (h.era != 0) {
                sb.append(", spoken by ").append(h.era);
            }
            if (!h.where.isEmpty()) {
                sb.append(", in ").append(h.where);
            }
            System.out.println(sb);
            for (String v : h.vox) {
                System.out.println("                 vox " + v);
                System.out.println("                     " + phonemes(v));
            }
            for (String s : h.say) {
                System.out.println("                 say " + s);
            }
            for (String a : h.act) {
                System.out.println("                 do  " + a);
            }
        }
    }

    // ========================================================================= main

    private static void usage() {
        System.err.println("""
                usage: Herobrine [options] <world.haunt>
                  --check              parse, print the schedule, exit
                  --dry                run the night to stdout as fast as it computes
                  --fast               50 ms to the second
                  --for <seconds>      how long the night is (default: forever, 300 when dry)
                  --seed <n>           override the world's seed
                  --era <year>         1950, 1962, 1980 or 1990
                  --speak <text>       say it, write a wav, exit
                  --out <file>         where --speak writes (default: say.wav)
                  --urgency <0-100>    how hard the announcer is pushing
                  --pronounce <text>   print the phonemes and exit
                  --repl               type a line, write a wav, exit on a blank one""");
        System.exit(1);
    }

    private static void die(String msg, String detail) {
        System.err.println("herobrine: " + msg + (detail != null ? " '" + detail + "'" : ""));
        System.exit(1);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        boolean check = false, dry = false, fast = false, repl = false;
        long seconds = -1;
        Long seed = null;
        Integer era = null;
        String speak = null, pronounceArg = null, path = null;
        String wavOut = "say.wav";
        double urgency = 0.0;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--check" -> check = true;
                case "--dry" -> dry = true;
                case "--fast" -> fast = true;
                case "--repl" -> repl = true;
                case "--for" -> seconds = Long.parseLong(args[++i]);
                case "--seed" -> seed = Long.parseUnsignedLong(args[++i]);
                case "--era" -> era = Integer.parseInt(args[++i]);
                case "--speak" -> speak = args[++i];
                case "--pronounce" -> pronounceArg = args[++i];
                case "--out" -> wavOut = args[++i];
                case "--urgency" -> urgency = Double.parseDouble(args[++i]) / 100.0;
                default -> {
                    if (a.startsWith("-")) {
                        usage();
                    } else if (path == null) {
                        path = a;
                    } else {
                        usage();
                    }
                }
            }
        }

        if (era != null && !eraKnown(era)) {
            die("no engine from that year. try 1950, 1962, 1980 or 1990.", era.toString());
        }
        int voiceEra = era != null ? era : DEFAULT_ERA;

        // type a line, hear it. the voice can only be judged by ear.
        if (repl) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.err.println("herobrine: " + voiceEra + ". type a line, blank to leave.");
            String line;
            while (true) {
                System.err.print("say> ");
                System.err.flush();
                line = in.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }

                // an era can be changed mid-loop: "1950 attention."
                String text = line;
                int space = line.indexOf(' ');
                if (space > 0 && Character.isDigit(line.charAt(0))) {
                    try {
                        int y = Integer.parseInt(line.substring(0, space));
                        if (eraKnown(y)) {
                            voiceEra = y;
                            text = line.substring(space + 1);
                        }
                    } catch (NumberFormatException ignored) {
                        // not a year, so the whole line is the sentence
                    }
                }

                short[] pcm = speak(text, urgency, voiceEra);
                if (pcm.length == 0) {
                    System.err.println("  nothing sayable there");
                    continue;
                }
                Files.write(Path.of(wavOut), wav(pcm));
                System.err.printf("  %d, %.2f s -> %s%n",
                        voiceEra, (double) pcm.length / RATE, wavOut);
            }
            return;
        }

        // our own voice needs no world
        if (pronounceArg != null) {
            System.out.println(pronounceArg + " -> " + phonemes(pronounceArg));
            return;
        }
        if (speak != null) {
            short[] pcm = speak(speak, urgency, voiceEra);
            if (pcm.length == 0) {
                die("nothing sayable in", speak);
            }
            Files.write(Path.of(wavOut), wav(pcm));
            System.err.printf("herobrine: %.2f s of announcer -> %s%n",
                    (double) pcm.length / RATE, wavOut);
            return;
        }

        if (path == null) {
            usage();
        }

        String src = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        World world;
        try {
            world = parse(src, seed, era);
        } catch (HauntSyntaxException e) {
            die(e.getMessage(), null);
            return;
        }
        if (world.haunts.isEmpty()) {
            die("no haunts in", path);
        }

        if (check) {
            printCheck(world, path);
            return;
        }

        if (seconds < 0 && dry) {
            seconds = 300;
        }
        System.err.printf("herobrine: %s, seed %s, %d haunts%s%n",
                world.name, Long.toUnsignedString(world.seed), world.haunts.size(),
                fast ? ", fast" : "");
        runNight(world, seconds, fast ? 50 : (dry ? 0 : 1000), STDOUT);
    }
}
