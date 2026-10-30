package br.ucsal.sd.arena.cliente;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class ClienteApp extends Application {

    @Override
    public void start(Stage stage) {
        Label titulo = new Label("Arena de Elementais — conectando...");
        StackPane raiz = new StackPane(titulo);
        raiz.setAlignment(Pos.CENTER);

        stage.setTitle("Arena de Elementais");
        stage.setScene(new Scene(raiz, 640, 400));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
