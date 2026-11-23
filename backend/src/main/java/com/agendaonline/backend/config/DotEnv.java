package com.agendaonline.backend.config;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepara a configuração antes de o Spring subir.
 *
 * <ol>
 *   <li>Em desenvolvimento, lê {@code .env.local} / {@code .env} (ignorados pelo Git) e expõe cada
 *       variável como propriedade de sistema — assim {@code ${JWT_SECRET}} etc. funcionam no
 *       {@code application.properties}. Variáveis reais do ambiente sempre têm prioridade.</li>
 *   <li>Converte a conexão do Neon ({@code postgresql://usuario:senha@host/banco?sslmode=require})
 *       para o formato que o JDBC espera (URL, usuário e senha separados).</li>
 * </ol>
 *
 * Em produção (Cloud Run, Render…) basta definir as variáveis de ambiente; nenhum arquivo é lido.
 */
public final class DotEnv {

    /** Procura na pasta atual e em {@code backend/} (caso a aplicação seja iniciada pela raiz do repositório). */
    private static final List<String> FILES = List.of(".env.local", ".env", "backend/.env.local", "backend/.env");

    private DotEnv() {
    }

    public static void load() {
        readFiles().forEach((key, value) -> {
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        });

        String databaseUrl = get("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return; // o Spring vai reclamar da falta de datasource com uma mensagem clara
        }
        JdbcConnection db = toJdbc(databaseUrl);
        setIfAbsent("spring.datasource.url", db.url());
        setIfAbsent("spring.datasource.username", db.username());
        setIfAbsent("spring.datasource.password", db.password());
    }

    /** Converte {@code postgres(ql)://user:pass@host[:port]/db?params} em URL JDBC + credenciais. */
    public static JdbcConnection toJdbc(String databaseUrl) {
        URI uri = URI.create(databaseUrl.trim());
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException("DATABASE_URL deve começar com postgresql://");
        }

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            username = decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            password = colon >= 0 ? decode(userInfo.substring(colon + 1)) : null;
        }

        StringBuilder url = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            url.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        url.append(path == null || path.isEmpty() ? "/" : path);

        // Mantém só o sslmode; o driver JDBC negocia SCRAM sozinho (channel_binding é parâmetro do libpq).
        String sslmode = "require";
        if (uri.getRawQuery() != null) {
            for (String param : uri.getRawQuery().split("&")) {
                if (param.startsWith("sslmode=")) {
                    sslmode = decode(param.substring("sslmode=".length()));
                }
            }
        }
        url.append("?sslmode=").append(sslmode);

        return new JdbcConnection(url.toString(), username, password);
    }

    private static Map<String, String> readFiles() {
        Map<String, String> vars = new LinkedHashMap<>();
        for (String file : FILES) {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    int eq = trimmed.indexOf('=');
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, eq).trim();
                    if (key.startsWith("export ")) {
                        key = key.substring("export ".length()).trim();
                    }
                    vars.putIfAbsent(key, unquote(trimmed.substring(eq + 1).trim()));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível ler " + path.toAbsolutePath(), e);
            }
        }
        return vars;
    }

    static String unquote(String value) {
        boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
        boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
        if (value.length() >= 2 && (doubleQuoted || singleQuoted)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String get(String key) {
        String env = System.getenv(key);
        return env != null ? env : System.getProperty(key);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void setIfAbsent(String property, String value) {
        if (value != null && System.getProperty(property) == null) {
            System.setProperty(property, value);
        }
    }

    public record JdbcConnection(String url, String username, String password) {
    }
}
