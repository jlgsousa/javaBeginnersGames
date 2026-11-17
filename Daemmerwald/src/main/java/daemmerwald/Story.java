package daemmerwald;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static daemmerwald.Look.rgb;

/** Alle Texte, Figuren und ihre Prüfungen. */
public final class Story {
    private Story() { }

    public static final String[] INTRO = {
        "Wurzelhain. Eine Akademie, gewachsen aus den Wurzeln eines Baumes, der älter ist als jede Krone. "
      + "Dreihundert Jahre lang hat der Pakt gehalten: Die Trolle hüten die Steine, die Feen das Licht, "
      + "und die Akademie lehrt beide – im Namen eines Friedens, den niemand mehr hinterfragt.",

        "Dann kam die Welke. Zuerst verloren die Birken am Mondsee ihre Farbe. Dann wurden die Quellen im Steinbruch bitter. "
      + "Heute frisst sie sich in violetten Adern durch den Dämmerwald, und mit jedem toten Baum werden die Stimmen im Rat lauter. "
      + "Die Clans beschuldigen den Feenhof. Der Hof beschuldigt die Clans.",

        "Die Akademie schweigt. Und in Wurzelhain hat Schweigen immer einen Preis. "
      + "Der Paktstein, der die Völker einst band, wurde vor vierzig Jahren in sechs Splitter zerschlagen "
      + "und an jene verteilt, die ihn hüten sollten. Wer alle sechs zum Herzbaum trägt, kann den Pakt erneuern – oder ihn brechen.",

        "Du bist im letzten Jahr deines Studiums. Niemand hat dich gebeten, das zu tun.\n\nGenau deshalb wirst du es tun."
    };

    public static final Look BORGA = Look.troll(rgb(116, 136, 104), rgb(92, 70, 50), rgb(240, 180, 90), rgb(50, 40, 30), false).hair(true, Look.BRAIDS);
    public static final Look ILVY = Look.fairy(rgb(240, 225, 215), rgb(90, 140, 170), rgb(160, 230, 255), rgb(200, 120, 60), rgb(170, 220, 255)).hair(true, Look.PONYTAIL);
    public static final Look REKTOR = Look.fairy(rgb(215, 210, 200), rgb(70, 34, 78), rgb(230, 200, 120), rgb(230, 230, 230), rgb(150, 140, 160)).hair(false, Look.BEARD);
    public static final Look VELMARA = Look.fairy(rgb(225, 215, 200), rgb(40, 40, 92), rgb(150, 180, 255), rgb(120, 120, 150), rgb(100, 110, 150)).hair(true, Look.BUN);
    public static final Look OBERION = Look.fairy(rgb(245, 235, 230), rgb(180, 180, 222), rgb(220, 240, 255), rgb(250, 250, 250), rgb(220, 230, 255)).hair(false, Look.LONG);
    public static final Look GRUMHILD = Look.troll(rgb(110, 116, 126), rgb(100, 80, 60), rgb(255, 170, 80), rgb(210, 210, 210), true).hair(true, Look.BUN);
    public static final Look YSOLDE = Look.troll(rgb(100, 122, 90), rgb(50, 60, 40), rgb(150, 255, 120), rgb(40, 50, 30), false).hair(true, Look.LONG);
    public static final Look ORAKEL = new Look(Look.SHROOM, new Color(220, 210, 185), new Color(150, 52, 42),
        new Color(255, 236, 170), Color.WHITE, Color.WHITE, false);
    public static final Look KESTRA = Look.fairy(rgb(210, 190, 170), rgb(70, 60, 40), rgb(255, 200, 120), rgb(150, 60, 40), rgb(200, 170, 120)).hair(true, Look.BRAIDS);
    public static final Look CORVINA = new Look(Look.WRAITH, Color.BLACK, new Color(60, 24, 72),
        new Color(214, 120, 255), Color.BLACK, Color.BLACK, false);

    public static final Look VEX = new Look(Look.WRAITH, Color.BLACK, new Color(20, 24, 40),
        new Color(255, 80, 90), Color.BLACK, Color.BLACK, false);
    public static final Look AURIN = Look.fairy(rgb(255, 240, 225), rgb(255, 200, 120), rgb(255, 250, 170), rgb(255, 230, 120), rgb(255, 250, 200)).hair(true, Look.LONG);

    public static final Look BRUMM = Look.troll(rgb(126, 140, 100), rgb(150, 70, 40), rgb(255, 200, 80), rgb(180, 120, 60), false).hair(false, Look.WILD);
    public static final Look GROL = Look.troll(rgb(128, 128, 132), rgb(96, 96, 100), rgb(120, 220, 255), rgb(110, 110, 115), true).hair(false, Look.BEARD);
    public static final Look PIMPERNELL = new Look(Look.MOUSE, new Color(140, 128, 120), new Color(200, 80, 90),
        new Color(255, 220, 230), Color.WHITE, Color.WHITE, false);

    /** Aurin wählt eine zufällige Prüfung. */
    public static MiniGame randomGame(GamePanel g) {
        return switch (new java.util.Random().nextInt(7)) {
            case 0 -> new RuneTraceGame(g);
            case 1 -> new WispHuntGame(g);
            case 2 -> new StoneStackGame(g);
            case 3 -> new PotionGame(g);
            case 4 -> new MemoryGame(g);
            case 5 -> new RiddleGame(g);
            default -> new ThornFlightGame(g);
        };
    }

    public static List<Npc> createNpcs() {
        List<Npc> l = new ArrayList<>();

        Npc borga = new Npc("borga", "Borga", BORGA, 42, 19);
        borga.before = new String[]{
            "Du hast dich also wirklich gemeldet. Mutig. Oder dumm. In Wurzelhain ist das selten ein Unterschied.",
            "Sechs Splitter. Magistra Velmara in der Bibliothek hat einen. Der Rest liegt draußen: Mondsee im Westen, Steinbruch im Osten, das Moor im Süden.",
            "Hinter dem Moor, im Südosten, liegt die Dornenschlucht. Die Welke hat den Eingang zugewuchert. Ohne Lichtzauber kommst du da nicht durch.",
            "Laufen mit WASD oder den Pfeiltasten. [E] zum Sprechen. [Leertaste] wirkt deinen Lichtzauber – der brennt die Welke weg, kostet aber Mana.",
            "[M] zeigt die Karte, [Esc] öffnet dein Tagebuch. Und wenn du dem Rektor begegnest … glaub nicht alles, was er sagt."
        };
        l.add(borga);

        Npc ilvy = new Npc("ilvy", "Ilvy", ILVY, 49, 21);
        ilvy.before = new String[]{
            "Meine Mutter sitzt im Hofrat. Sie sagt, die Trolle hätten die Quellen vergiftet.",
            "Sie sagt das sehr überzeugt – für jemanden, der noch nie im Steinbruch war.",
            ">Und was glaubst du?",
            "Ich glaube, alle wissen mehr, als sie zugeben. Das ist das Einzige, worauf sich Trolle und Feen noch einigen können."
        };
        l.add(ilvy);

        Npc rektor = new Npc("rektor", "Rektor Aldric Venn", REKTOR, 50, 7);
        rektor.before = new String[]{
            "Ah. {name}. Ich habe von deinem … Engagement gehört.",
            "Die Welke ist eine natürliche Erscheinung. Zyklen, verstehst du? Wälder sterben, Wälder wachsen. Die Akademie hat alles im Griff.",
            ">Und warum sind die Splitter des Paktsteins dann über den ganzen Wald verstreut?",
            "Weil Macht, die an einem Ort liegt, Begehrlichkeiten weckt. Geh in den Unterricht, {name}. Das ist ein freundlicher Rat. Ich gebe nicht viele davon."
        };
        rektor.after = new String[]{
            "Du warst fleißig, wie ich höre.",
            "Ich hoffe, du weißt, was du da mit dir herumträgst. Manche Wahrheiten halten ein Haus zusammen – ganz gleich, wie morsch das Fundament ist.",
            ">Und manche Häuser sollte man vielleicht nicht zusammenhalten.",
            "*Der Rektor lächelt. Seine Hände zittern."
        };
        l.add(rektor);

        Npc vel = new Npc("velmara", "Magistra Velmara", VELMARA, 39, 7);
        vel.shard = Shard.WISSEN;
        vel.game = RuneTraceGame::new;
        vel.before = new String[]{
            "Leise. Hier drin hören selbst die Bücher mit.",
            "Du suchst den Splitter des Wissens. Ich habe ihn seit vierzig Jahren in einer Schublade, die niemand öffnet. Das war die Abmachung.",
            "Aber Abmachungen altern schlecht. Wie Pergament. Wie die Leute, die sie unterschreiben.",
            "Zeig mir, dass du die alten Runen lesen kannst. Zieh sie mit gedrückter Maustaste nach – in einem Zug, in der richtigen Reihenfolge."
        };
        vel.win = new String[]{
            "Sauber. Beinahe arrogant sauber.",
            "Nimm ihn. Und wenn du schon Fragen stellst: Frag dich, wohin das Licht des Herzbaums fließt. Achte beim Rektor auf seine Hände, nicht auf seine Worte."
        };
        vel.lose = new String[]{"Die Runen verzeihen keine Hast. Komm wieder, wenn dein Kopf klarer ist."};
        vel.after = new String[]{"Vierzig Jahre. Und am Ende hat es nur jemanden wie dich gebraucht. Und ein wenig Mut, den ich nicht hatte."};
        vel.note = "Velmara: Das Licht des Herzbaums fließt irgendwohin. Der Rektor weiß, wohin.";
        l.add(vel);

        Npc ober = new Npc("oberion", "Kanzler Oberion", OBERION, 23, 30);
        ober.shard = Shard.LICHT;
        ober.game = WispHuntGame::new;
        ober.before = new String[]{
            "Der Hof empfängt keine Bittsteller ohne Termin. Aber gut – die Königin ist in Trauer, und ich bin gelangweilt.",
            "Die Irrlichter des Mondsees sind die Erinnerungen derer, die hier gestorben sind. Seit die Welke kommt, mischen sich Schatten darunter.",
            "Fang die goldenen Lichter mit der Maus. Die dunklen lass in Ruhe – sie sind hungrig und nehmen dir, was du gesammelt hast.",
            "Gelingt es dir, gehört der Splitter des Lichts dir. Der Hof kann sich ein wenig Großzügigkeit leisten. Öffentlich zumindest."
        };
        ober.win = new String[]{
            "Bemerkenswert. Ich werde der Königin sagen, es sei meine Idee gewesen.",
            "Ein Rat unter uns: Der Hof weiß, dass die Trolle die Quellen nicht vergiftet haben. Aber ein Feind ist so viel bequemer als eine Frage."
        };
        ober.lose = new String[]{"Die Toten sind wählerisch. Versuch es noch einmal, wenn du ruhiger atmest."};
        ober.after = new String[]{"Politik, {name}, ist die Kunst, im richtigen Moment das Falsche zu glauben."};
        ober.note = "Oberion: Der Feenhof weiß, dass die Trolle unschuldig sind – und schweigt aus Bequemlichkeit.";
        l.add(ober);

        Npc grum = new Npc("grumhild", "Clanälteste Grumhild", GRUMHILD, 75, 25);
        grum.shard = Shard.STEIN;
        grum.game = StoneStackGame::new;
        grum.before = new String[]{
            "Ein Akademiekind im Steinbruch. Die letzten von euch kamen mit Verträgen. Und gingen mit unseren Quellen.",
            "Du willst den Splitter des Steins? Dann zeig, dass du etwas bauen kannst, statt nur zu reden.",
            "Stapel die Runensteine. [Leertaste] oder Klick, wenn der Stein über dem Turm liegt. Was übersteht, bricht weg – wie bei jedem Bündnis.",
            "Zwölf Lagen. Dann reden wir weiter."
        };
        grum.win = new String[]{
            "Hm. Gerade, ehrlich, ohne Schnörkel. Das gefällt mir.",
            "Nimm den Splitter. Und hör zu: Vor vierzig Jahren hat die Akademie unsere Quellen umgeleitet. Zum Herzbaum, hieß es. Zum Schutz. Seitdem ist das Wasser bitter."
        };
        grum.lose = new String[]{"Eingestürzt. Passiert. Mein Großvater hat drei Häuser gebaut, bevor eins stand."};
        grum.after = new String[]{"Die Welke hat meinen Enkeln ihr Zuhause genommen, {name}. Sie leben jetzt in der Stadt, zwischen Leuten, die sie Steinköpfe nennen. Manchmal weiß ich nicht, was schlimmer ist."};
        grum.note = "Grumhild: Die Akademie hat vor 40 Jahren die Quellen der Trolle zum Herzbaum umgeleitet.";
        l.add(grum);

        Npc ys = new Npc("ysolde", "Moorhexe Ysolde", YSOLDE, 44, 57);
        ys.shard = Shard.WASSER;
        ys.game = PotionGame::new;
        ys.before = new String[]{
            "Riechst du das? Torf, Rauch und Lügen. Das Moor erzählt alles, wenn man lange genug still sitzt.",
            "Ich brauche einen Trank, der die Welke zurückhält. Für die Moorleute, die noch nicht weggezogen sind.",
            "Ich zeige dir die Zutaten. Merk dir die Reihenfolge und gib sie genauso in den Kessel – per Maus oder mit den Tasten 1 bis 6.",
            "Drei Fehler, und der Kessel kocht über. Dann riecht hier alles eine Woche lang nach verbrannten Zehennägeln."
        };
        ys.win = new String[]{
            "Hah! Du hast ein Händchen. Und kein Gewissen, das dir dazwischenfunkt. Das mag ich.",
            "Der Splitter des Wassers gehört dir. Und weil du nett warst: Die Welke ist kein Gift. Sie ist ein Gefühl. Trauer, die zu lange eingesperrt war. Auch Bäume können trauern."
        };
        ys.lose = new String[]{"Übergekocht. Wie meine dritte Ehe. Noch mal."};
        ys.after = new String[]{"Komm wieder, wenn das alles vorbei ist. Ich mach dir einen Tee, der dich vergessen lässt. Oder erinnern. Je nachdem, was du brauchst."};
        ys.note = "Ysolde: Die Welke ist kein Gift, sondern eingesperrte Trauer.";
        l.add(ys);

        Npc ora = new Npc("orakel", "Das Pilzorakel", ORAKEL, 15, 58);
        ora.shard = Shard.ERINNERUNG;
        ora.game = MemoryGame::new;
        ora.before = new String[]{
            "Wir sind viele. Wir sind einer. Wir erinnern uns an alles, was unter der Erde geschieht.",
            "Die Wurzeln flüstern einen Namen, den oben niemand mehr ausspricht: Corvina.",
            "Finde die Paare in unseren Erinnerungen, und wir geben dir den Splitter der Erinnerung."
        };
        ora.win = new String[]{
            "Du hast gesehen. Nun höre: Corvina lehrte an der Akademie. Sie fand heraus, dass der Rektor das Leben des Herzbaums in die Mauern der Akademie leitet.",
            "Sie wollte es dem Rat sagen. Man band sie an den Baum – nicht tot, nicht lebendig. Ihr Zorn wurde zur Welke. Ihre Trauer auch."
        };
        ora.lose = new String[]{"Vergessen ist leicht. Erinnern ist Arbeit. Noch einmal."};
        ora.after = new String[]{"Corvina wartet. Seit vierzig Jahren. Sie ist nicht deine Feindin. Aber sie wird dich nicht erkennen."};
        ora.note = "Orakel: Magistra Corvina entdeckte, dass der Rektor dem Herzbaum das Leben entzieht. Sie wurde an den Baum gebunden. Ihr Zorn ist die Welke.";
        l.add(ora);

        Npc kes = new Npc("kestra", "Fährtenleserin Kestra", KESTRA, 77, 56);
        kes.shard = Shard.WIND;
        kes.game = ThornFlightGame::new;
        kes.before = new String[]{
            "Du hast dich durch die Welke gebrannt. Nicht schlecht. Die meisten drehen vorher um.",
            "Der Splitter des Windes liegt auf der anderen Seite der Schlucht. Der einzige Weg führt durch die Dornen.",
            "Ich leih dir meine Flugform. Weich den Ranken aus – Pfeiltasten oder WASD. Drei Treffer, und du landest im Dreck."
        };
        kes.win = new String[]{
            "Du fliegst wie jemand, der etwas zu verlieren hat. Gut so.",
            "Hier, der Splitter. Ich hab Leute gesehen, die bei dem Versuch alles verloren haben, was sie einmal waren. Pass auf dich auf."
        };
        kes.lose = new String[]{"Autsch. Die Dornen merken sich Gesichter. Ruh dich aus und versuch's noch mal."};
        kes.after = new String[]{"Wenn du zum Herzbaum gehst: Geh nicht allein in deinem Kopf. Denk an die Leute, für die du das machst."};
        kes.note = "Kestra: Am Herzbaum wartet etwas, das einen Menschen verschlucken kann.";
        l.add(kes);

        Npc vex = new Npc("vex", "Nachtmahr Vex", VEX, 14, 15);
        vex.game = g -> new DuelGame(g, "Nachtmahr Vex", VEX);
        vex.before = new String[]{
            "*Zwischen den toten Bäumen steht eine Gestalt, die keinen Schatten wirft – weil sie selbst einer ist.",
            "Ein Lichtkind im Schattenhain. Wie erfrischend. Die meisten trauen sich nur bis zum Waldrand.",
            "Ich bin, was übrig bleibt, wenn dunkle Magie zu lange in einem Ort wohnt. Kein Dämon. Eher … Restwärme.",
            "Ich langweile mich, {name}. Duellier dich mit mir. Immer wieder, wenn du willst. Jedes Mal werde ich stärker – und du hoffentlich auch."
        };
        vex.after = vex.before;
        vex.win = new String[]{"Hah. Du schmeckst nach Sonnenlicht und Trotz. Komm wieder. Ich werde mich vorbereiten."};
        vex.lose = new String[]{"Nicht schlecht für jemanden mit Puls. Ruh dich aus. Die Nacht läuft nicht weg."};
        l.add(vex);

        Npc aurin = new Npc("aurin", "Lichtfee Aurin", AURIN, 74, 11);
        aurin.game = Story::randomGame;
        aurin.before = new String[]{
            "Oh! Besuch! Willkommen auf der Lichtwiese – dem einzigen Ort im Wald, an dem die Welke noch nie war.",
            "Ich weiß nicht, warum. Vielleicht, weil hier zu viel gelacht wird. Trauer mag kein Gelächter.",
            "Lust auf ein Spiel? Ich such eins aus – du weißt vorher nie, welches! Dafür gibt's bei mir anderthalbmal so viel Erfahrung."
        };
        aurin.after = new String[]{"Noch eine Runde? Ich hab da so ein Gefühl … das wird diesmal ganz anders!"};
        aurin.win = new String[]{"Juhu! Siehst du, die Wiese freut sich mit dir. Die Blumen leuchten sogar heller."};
        aurin.lose = new String[]{"Ach, Pech! Aber Pech ist nur Glück, das sich verlaufen hat. Gleich noch mal?"};
        l.add(aurin);

        Npc brumm = new Npc("brumm", "Brumm der Läufer", BRUMM, 0, 0);
        brumm.before = new String[]{
            "Hoho! Ein Akademiekind mit Beinen! Ich bin Brumm. Ich laufe. Das ist alles, was ich kann – aber das kann ich gut.",
            "Lust auf ein Rennen? Folge den leuchtenden Ringen quer durch den Wald. Wer zuerst durch den letzten Ring läuft, gewinnt.",
            "[Umschalt] hilft, wenn du den Windschritt schon kannst. Und wenn nicht – dann lauf eben wie ein Troll: einfach weiter."
        };
        brumm.after = new String[]{"Noch eine Runde? Ich hab mich warmgelaufen. Diesmal bin ich schneller, versprochen."};
        brumm.win = new String[]{"Was?! Du warst schneller als ich? Meine Großmutter wird mich auslachen. Komm wieder – ich trainiere!"};
        brumm.lose = new String[]{"Hoho! Erster! Wie immer! Aber du warst nah dran. Fast. Ein bisschen. Na gut, nicht wirklich."};
        l.add(brumm);

        Npc grol = new Npc("grol", "Steinwächter Grol", GROL, 0, 0);
        grol.game = RiddleGame::new;
        grol.before = new String[]{
            "*Die Statue am Wegrand öffnet die Augen. Kleine Steine bröckeln von ihren Lidern.",
            "Ich bewache diesen Weg seit dreihundert Jahren. Niemand hat mich je gebeten, damit aufzuhören.",
            "Beantworte meine Rätsel, Wanderer. Jedes Mal, wenn du gewinnst, denke ich mir schwierigere aus. Ich habe Zeit."
        };
        grol.after = new String[]{"Du bist zurück. Gut. Ich habe nachgedacht. Das tue ich meistens."};
        grol.win = new String[]{"Klug. Nimm diese Taler – ich habe sie von Wanderern, die nicht so klug waren."};
        grol.lose = new String[]{"Denke nach. Ich warte. Ich bin gut im Warten."};
        l.add(grol);

        Npc pim = new Npc("pimpernell", "Mäusewirtin Pimpernell", PIMPERNELL, 0, 0);
        pim.before = new String[]{
            "Oh! Kundschaft! Nicht so stürmisch, meine Schnurrhaare sind frisch gekämmt.",
            "Willkommen bei »Pimpernells Fadenwerk« – Gewänder mit Zauber im Saum! Für Trolle in Übergröße, für Feen mit Flügelschlitz.",
            "Bezahlt wird in Glimmertalern. Die verdienst du mit Prüfungen, Rennen, Aufträgen und beim Sammeln. Schau dich um!"
        };
        l.add(pim);

        Npc k1 = new Npc("kessel1", "Ysoldes Braukessel", null, 0, 0);
        k1.radius = 3;
        l.add(k1);
        Npc k2 = new Npc("kessel2", "Kessel der Akademie", null, 0, 0);
        k2.radius = 3;
        l.add(k2);
        Npc cafe = new Npc("cafe", "Café Wolkenrand", null, 0, 0);
        cafe.radius = 4;
        l.add(cafe);

        Npc heart = new Npc("heart", "Der Herzbaum", null, 45, 40.4);
        heart.radius = 80;
        heart.game = g -> new DuelGame(g, "Corvinas Echo", CORVINA);
        heart.lose = DUEL_LOST;
        heart.win = new String[]{"*Das Echo sinkt zurück in die Wurzeln – für heute."};
        l.add(heart);
        return l;
    }

    public static String[] heartNotReady(int count) {
        return new String[]{
            "*Der Herzbaum ächzt. Violette Adern pulsieren unter der Rinde. Eine Stimme, kaum mehr als ein Rascheln: »… sechs … bring sie … alle …«",
            "*Du trägst " + count + " von 6 Splittern bei dir."
        };
    }

    public static final String[] HEART_READY = {
        "*Die Splitter in deiner Tasche beginnen zu singen. Der Boden unter dem Herzbaum bricht auf, und etwas erhebt sich aus den Wurzeln.",
        "Noch eins. Noch ein Kind, das der Rektor schickt, um mich wieder einzuschließen.",
        ">Niemand hat mich geschickt. Ich will verstehen, was hier passiert ist.",
        "Verstehen? Dann versteh DAS."
    };

    public static final String[] HEART_RENEWED = {
        "*Unter der Rinde atmet es ruhig und schwer. Ein Echo von Corvina regt sich in den Wurzeln – halb Traum, halb Erinnerung.",
        "Du schon wieder. Bleib nur. Die Nacht ist lang, und ich habe gelernt, mit dir zu ringen, statt dich zu hassen."
    };

    public static final String[] HEART_FREED = {
        "*Der Herzbaum treibt junge Blätter. Zwischen den Wurzeln flackert noch ein Rest der alten Welke – ein Echo, das nicht gehen will.",
        "Ich bin nur, was von ihrem Zorn übrig blieb. Zeig mir, dass er nicht mehr gebraucht wird."
    };

    public static final String[] DUEL_LOST = {
        "Geh. Komm wieder, wenn du weißt, wofür du kämpfst."
    };

    public static final String[] ENDING_TALK = {
        "*Der violette Strahl bricht. Die Gestalt sinkt in die Wurzeln, und für einen Moment siehst du ein Gesicht: müde, alt, sehr menschlich.",
        "Du … hältst stand. Warum?",
        ">Weil ich dich nicht besiegen will. Ich will, dass das aufhört.",
        "Dann entscheide. Die Splitter können den Pakt erneuern. Ich schlafe wieder, der Baum lebt weiter, die Akademie behält ihr Licht. Niemand erfährt etwas.",
        "Oder du brichst ihn. Ich bin frei. Der Baum heilt. Aber das Licht der Akademie erlischt – und jeder wird wissen, warum."
    };

    public static final String ENDING_RENEW =
        "Die Welke zieht sich zurück wie ein Atemzug, den jemand anhält. Die Akademie leuchtet weiter. "
      + "Der Rektor lobt dich vor dem Rat, und du nimmst den Applaus an, weil man ihn eben annimmt. "
      + "Trolle und Feen beschuldigen einander leiser – aber sie tun es noch.\n\n"
      + "Manchmal, in stillen Nächten, hörst du unter der Erde jemanden atmen.\n\n"
      + "Du hast den Frieden bewahrt. Du bist nicht sicher, ob er es wert war.";

    public static final String ENDING_BREAK =
        "Als der Pakt zerbricht, erlöschen die Lichter von Wurzelhain eines nach dem anderen. "
      + "Corvina tritt aus den Wurzeln, alt und müde und frei, und spricht vor dem Rat. "
      + "Der Rektor tritt zurück, bevor man ihn dazu zwingt.\n\n"
      + "Der Winter wird hart. Clans und Hof müssen zum ersten Mal seit dreihundert Jahren miteinander reden, "
      + "ohne dass jemand für sie übersetzt. Es ist chaotisch, laut und ungerecht.\n\n"
      + "Aber im Frühjahr treibt der Herzbaum neue Blätter aus.";
}
