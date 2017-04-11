/**
 * Created by Juliang on 1/6/16.
 */

package com.coursepicker;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.*;
import javafx.fxml.FXMLLoader;

public class Main extends Application{
    private Parent root;
    @Override
    public void start(Stage primaryStage) throws Exception{
        final FXMLLoader guiLoader = new FXMLLoader(getClass().getResource("CoursePickerGUI.fxml"));
    }
    public static void main(String[] args) {
        launch(args);
    }
}
