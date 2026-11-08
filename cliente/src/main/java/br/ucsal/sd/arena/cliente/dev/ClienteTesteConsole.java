package br.ucsal.sd.arena.cliente.dev;

import br.ucsal.sd.arena.cliente.rede.ConexaoServidor;
import br.ucsal.sd.arena.cliente.rede.OuvinteConsole;
import br.ucsal.sd.arena.compartilhado.excecao.NomeEmUsoException;

/** Cliente de teste sem GUI. Uso: {@code ClienteTesteConsole host porta nome [ipLocal]}. Enter desconecta. */
public final class ClienteTesteConsole {

    private ClienteTesteConsole() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Uso: ClienteTesteConsole <host> <porta> <nome> [ipLocal]");
            System.exit(2);
        }
        String nome = args[2];
        String ipLocal = args.length > 3 ? args[3] : null;

        ConexaoServidor conexao = new ConexaoServidor(args[0], Integer.parseInt(args[1]), ipLocal);
        try {
            conexao.conectar(nome, new OuvinteConsole(nome));
        } catch (NomeEmUsoException e) {
            System.out.println("[" + nome + "] NomeEmUsoException: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("[" + nome + "] Conectado. Token=" + conexao.getToken() + ". Pressione Enter para sair.");
        System.in.read();
        conexao.desconectar();
        System.out.println("[" + nome + "] Desconectado.");
        System.exit(0);
    }
}
