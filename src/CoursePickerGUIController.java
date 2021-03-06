/**
 * Created by Juliang on 4/7/16.
 * Updated to add new functions by Jeffrey Cordero 12/21/2016
 */
import CoursePlanner.DataFileIO;
import CoursePlanner.PastCourse;
import CoursePlanner.ScheduleMaker.FutureCourse;
import CoursePlanner.ScheduleMaker.ScheduleDataGetter;
import CoursePlanner.ScheduleMaker.SchedulePlanner;
import CoursePlanner.ScheduleMaker.User;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import jfxtras.scene.control.agenda.*;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.control.ProgressIndicator;

public class CoursePickerGUIController {
    //elements from fxml
    @FXML
    private TextField yearInputBox;
    @FXML
    private ChoiceBox<ScheduleDataGetter.Semester> semesterDropDownList;
    @FXML
    private ChoiceBox<String> subjectDropDownList;
    @FXML
    private TextField courseNumberField;
    @FXML
    private Button fetchDataButton;
    @FXML
    private Button helpButton;
    @FXML
    private Button infoButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button loadButton;
    @FXML
    /**
     * Courses displayed in left "available courses" box
     * Listview filled from fetchDataButton
     */
    private ListView<FutureCourse> courseListView;
    @FXML
    private TextArea courseDetailTextView;
    @FXML
    private Button addCourseButton;
    @FXML
    private Pane schedulePane;
    @FXML
    private Pane graphPane;
    private ToggleGroup chartToggle = new ToggleGroup();
    @FXML
    private RadioButton pieChartToggleButton;
    @FXML
    private RadioButton barChartToggleButton;
    @FXML
    /**
     * Courses displayed in right "selected courses" box
     * Listview filled from addCourseBurron and removed with deleteCourseButton
     */
    private ListView<FutureCourse> selectedCourseListView;
    @FXML
    private Button deleteCourseButton;
    @FXML
    private TextArea summaryTextView;
    private Agenda scheduleView = new Agenda();
    private Stage rootStage;
    private Stage loadingStage;
    private ProgressIndicator indicator;
    private Stage infoStage;
    private Stage loadWarningStage;
    private Task<List<FutureCourse>> dataFetchingTask;
    private Desktop desktop = Desktop.getDesktop();

    private void initGUI(){
        //initialize the scheduleView
        scheduleView.setMaxSize(schedulePane.getWidth(),schedulePane.getHeight());
        scheduleView.allowDraggingProperty().set(false);
        scheduleView.allowResizeProperty().set(false);
        schedulePane.getChildren().addAll(scheduleView);
        yearInputBox.setText(Year.now().toString());
        semesterDropDownList.getItems().addAll(ScheduleDataGetter.Semester.SPRING,ScheduleDataGetter.Semester.SUMMER,
                                                ScheduleDataGetter.Semester.FALL);
        semesterDropDownList.getSelectionModel().selectLast();
        subjectDropDownList.getItems().addAll(SchedulePlanner.getInstance().getAllSubjects());
        subjectDropDownList.getSelectionModel().select("CSCE");
        courseNumberField.setText("314");
        summaryTextView.setText(User.currentUser().toString());
        summaryTextView.setWrapText(true);
        courseDetailTextView.setWrapText(true);

        pieChartToggleButton.setToggleGroup(this.chartToggle);
        barChartToggleButton.setToggleGroup(this.chartToggle);

        this.loadingStage = getLoadingStage();
        this.infoStage = getInfoStage();
        this.loadWarningStage = getLoadWarningStage();
    }
    private void initAction(){
        /**
         * on click -> get data!!
         * Fetches data from website, creates object for each class (by section), then adds to courseListView
         */
        fetchDataButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try{
                    dataFetchingTask = new Task<List<FutureCourse>>() {
                        {
                            setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                                @Override
                                public void handle(WorkerStateEvent event) {
                                    courseListView.getItems().clear();
                                    courseListView.getItems().addAll(getValue());
                                    loadingStage.close();
                                }
                            });
                            setOnFailed(new EventHandler<WorkerStateEvent>() {
                                @Override
                                public void handle(WorkerStateEvent event) {
                                    loadingStage.close();
                                }
                            });
                        }
                        /**
                         * Takes input to query search
                         * @return fetchedCourses
                         * @throws Exception
                         */
                        @Override
                        protected List<FutureCourse> call() throws Exception {
                                List<FutureCourse> fetchedCourses = SchedulePlanner.getInstance().getCourse(yearInputBox.getText(),
                                        semesterDropDownList.getValue(),
                                        subjectDropDownList.getValue(),
                                        courseNumberField.getText()
                                );
                            return fetchedCourses;
                        }
                    };
                    dataFetchingTask.exceptionProperty().addListener(new ChangeListener<Throwable>() {
                        @Override
                        public void changed(ObservableValue<? extends Throwable> observable, Throwable oldValue, Throwable newValue) {
                            if (newValue != null) {
                                showAlert(newValue);
                            }
                        }
                    });
                    new Thread(dataFetchingTask).start();
                    loadingStage.showAndWait();
                }catch(Exception e){
                    loadingStage.close();
                    showAlert(e);
                }
            }
        });
        //on select -> present course summary and grade chart if there is one
        ChangeListener<FutureCourse> courseViewChangeListener = new ChangeListener<FutureCourse>() {
            @Override
            public void changed(ObservableValue<? extends FutureCourse> observable, FutureCourse oldValue, FutureCourse newValue) {
                if (newValue == null)
                    return;
                courseDetailTextView.setText(newValue.getDetail());
                try {
                    if (newValue.hasPastCourse()) {
                        makePieChart(newValue.getPastCourse());
                        chartToggle.selectToggle(pieChartToggleButton);
                    }else{
                        graphPane.getChildren().clear();
                    }
                }catch (Exception e){
                    showAlert(e);
                }
            }
        };
        courseListView.getSelectionModel().selectedItemProperty().addListener(courseViewChangeListener);
        selectedCourseListView.getSelectionModel().selectedItemProperty().addListener(courseViewChangeListener);

        // on select -> change the chart
        chartToggle.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            @Override
            public void changed(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue) {
                if (oldValue != null && oldValue.equals(newValue))
                    return;
                FutureCourse selectedFutureCourse = courseListView.getSelectionModel().getSelectedItem();
                if (selectedFutureCourse == null)
                    return;
                try {
                    if (selectedFutureCourse.hasPastCourse()) {
                        PastCourse selectedPastCourse = selectedFutureCourse.getPastCourse();
                        if (newValue.equals(pieChartToggleButton)){
                            makePieChart(selectedPastCourse);
                        }else{
                            makeBarChart(selectedPastCourse);
                        }
                    }
                }catch (Exception e){
                    showAlert(e);
                }
            }
        });

        /**
         * Button action for Add Course Button
         */
        addCourseButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FutureCourse selectedFutureCourse = courseListView.getSelectionModel().getSelectedItem();

                if (selectedFutureCourse == null)
                    return; //If no classes were selected on press
                addCourse(selectedFutureCourse);
            }
        });

        deleteCourseButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FutureCourse selectedFutureCourse = selectedCourseListView.getSelectionModel().getSelectedItem();
                if (selectedFutureCourse == null)
                    return;
                User.currentUser().removeCourse(selectedFutureCourse);
                selectedCourseListView.getItems().remove(selectedFutureCourse);
                List<Agenda.AppointmentImplLocal> listToRemove = appointmentMap.get(appointmentGroupMap.get(selectedFutureCourse.toString()));
                scheduleView.appointments().removeAll(listToRemove);
                appointmentGroupMap.remove(selectedFutureCourse.toString());
                appointmentMap.remove(appointmentGroupMap.get(selectedFutureCourse.toString()));
                appointmentGroupMap.remove(selectedFutureCourse.toString());
                summaryTextView.setText(User.currentUser().toString());
            }
        });

        infoButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                infoStage.showAndWait();
            }
        });

        helpButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    Desktop.getDesktop().browse(new URI("https://github.com/Juliang0705/CoursePicker"));
                }catch(Exception e){
                    showAlert(e);
                }
            }
        });

        saveButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Course Selections");
                fileChooser.setInitialFileName("filename.txt");
                FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("TXT file(*.txt)" ," *.txt");
                fileChooser.getExtensionFilters().add(extFilter);
                File file = fileChooser.showSaveDialog(rootStage);
                if(file != null) {
                    try{
                        DataFileIO fileIO = new DataFileIO(file);
                        fileIO.saveFile();
                    }catch (IOException ex){
                        showAlert(ex);
                    }
                }
            }
        });

        loadButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {

                List<FutureCourse> futureCourseList = new ArrayList<>();

                //Show load warning stage, only if classes were already added
                if (!selectedCourseListView.getItems().isEmpty()) loadWarningStage.showAndWait();
                //Show file chooser for file selection
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Load Course Selections");
                FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("TXT file(*.txt)" ," *.txt");
                fileChooser.getExtensionFilters().add(extFilter);
                File file = fileChooser.showOpenDialog(rootStage);
                if(file != null) {
                    try{
                        //Erase current data
                        if (!selectedCourseListView.getItems().isEmpty()) deleteAllCourses();
                        //Read file with load data:
                        DataFileIO fileIO = new DataFileIO(file);
                        futureCourseList = fileIO.readLoadFile();
                        //Add each course from file to program
                        for (FutureCourse selectedFutureCourse:futureCourseList)
                            addCourse(selectedFutureCourse);
                    }catch (IOException ex){
                        showAlert(ex);
                    }
                }
            }
        });
    }

    /*
     * Takes a FutureCourse object and adds it to the User object and schedule
     * Adds course to user object.  Adds course to appointment schedule of courses
     * @param FutureCourse Course to add
     */
    private void addCourse (FutureCourse selectedFutureCourse){
        try {
            User.currentUser().addCourse(selectedFutureCourse);//Check for conflicts then add to user object
            selectedCourseListView.getItems().add(selectedFutureCourse);//Add selected course to selected list
            String key = selectedFutureCourse.toString();
            Agenda.AppointmentGroupImpl newGroup = new Agenda.AppointmentGroupImpl();
            newGroup.setStyleClass("group" + (int) (Math.random() * 23 + 1));
            appointmentGroupMap.put(key, newGroup);

            //Adding class into agenda for graphic scheduling
            List<FutureCourse.TimeInterval> timeIntervalList = selectedFutureCourse.getTime();
            for (FutureCourse.TimeInterval time : timeIntervalList) {
                for (DayOfWeek day : time.getDays()) {
                    Agenda.AppointmentImplLocal newAppointment = new Agenda.AppointmentImplLocal()
                            .withStartLocalDateTime(LocalDate.from(day.adjustInto(LocalDate.now())).atTime(time.getStartHour(), time.getStartMinute()))
                            .withEndLocalDateTime(LocalDate.from(day.adjustInto(LocalDate.now())).atTime(time.getEndHour(), time.getEndMinute()))
                            .withSummary(selectedFutureCourse.toString())
                            .withAppointmentGroup(appointmentGroupMap.get(key));
                    if (!appointmentMap.containsKey(appointmentGroupMap.get(key))) {
                        appointmentMap.put(appointmentGroupMap.get(key), new ArrayList<>());
                    }
                    appointmentMap.get(appointmentGroupMap.get(key)).add(newAppointment);
                    scheduleView.appointments().add(newAppointment);
                }
            }
            summaryTextView.setText(User.currentUser().toString());//Add data to User Summary box
        } catch (Exception e) {
            showAlert(e);
        }
    }

    //HashMap used to store class scheduling data
    private HashMap<String,Agenda.AppointmentGroupImpl> appointmentGroupMap = new HashMap<>();
    private HashMap<Agenda.AppointmentGroupImpl,List<Agenda.AppointmentImplLocal>> appointmentMap = new HashMap<>();
    public void init(Stage s){
        this.rootStage = s;
        initGUI();
        initAction();
    }

    private void makePieChart(PastCourse c){
        this.graphPane.getChildren().clear();
        ObservableList<PieChart.Data> pieChartData =
                FXCollections.observableArrayList(
                        new PieChart.Data("A(" + String.format( "%.2f",c.getAPercentage()*100) +"%)", c.getA()),
                        new PieChart.Data("B(" + String.format( "%.2f",c.getBPercentage()*100) +"%)", c.getB()),
                        new PieChart.Data("C(" + String.format( "%.2f",c.getCPercentage()*100) +"%)", c.getC()),
                        new PieChart.Data("D(" + String.format( "%.2f",c.getDPercentage()*100) +"%)", c.getD()),
                        new PieChart.Data("F(" + String.format( "%.2f",c.getFPercentage()*100) +"%)", c.getF()),
                        new PieChart.Data("Q(" + String.format( "%.2f",c.getQPercentage()*100) +"%)", c.getQ())
                );
        PieChart chart = new PieChart(pieChartData);
        chart.setTitle(c.getInstructor());
        chart.setLabelsVisible(true);
        chart.setLabelLineLength(10);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.RIGHT);
        chart.setMaxSize(520,291);
        this.graphPane.getChildren().add(chart);
    }
    private void makeBarChart(PastCourse c){
        this.graphPane.getChildren().clear();
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        BarChart<String,Number> bc = new BarChart<>(xAxis,yAxis);
        xAxis.setLabel("Grade");
        yAxis.setLabel("Head Count");
        // A bar
        XYChart.Series seriesA = new XYChart.Series();
        seriesA.setName("A(" + String.format( "%.2f",c.getAPercentage()*100) +"%)");
        seriesA.getData().add(new XYChart.Data("A", c.getA()));
        //B bar
        XYChart.Series seriesB = new XYChart.Series();
        seriesB.setName("B(" + String.format( "%.2f",c.getBPercentage()*100) +"%)");
        seriesB.getData().add(new XYChart.Data("B", c.getB()));
        //C bar
        XYChart.Series seriesC = new XYChart.Series();
        seriesC.setName("C(" + String.format( "%.2f",c.getCPercentage()*100) +"%)");
        seriesC.getData().add(new XYChart.Data("C", c.getC()));
        //D bar
        XYChart.Series seriesD = new XYChart.Series();
        seriesD.setName("D(" + String.format( "%.2f",c.getDPercentage()*100) +"%)");
        seriesD.getData().add(new XYChart.Data("D", c.getD()));
        //F bar
        XYChart.Series seriesF = new XYChart.Series();
        seriesF.setName("F(" + String.format( "%.2f",c.getFPercentage()*100) +"%)");
        seriesF.getData().add(new XYChart.Data("F", c.getF()));
        //Q bar
        XYChart.Series seriesQ = new XYChart.Series();
        seriesQ.setName("Q(" + String.format( "%.2f",c.getQPercentage()*100) +"%)");
        seriesQ.getData().add(new XYChart.Data("Q", c.getQ()));
        //style
        bc.setTitle(c.getInstructor());
        bc.setLegendVisible(true);
        bc.setLegendSide(Side.BOTTOM);
        bc.setBarGap(-25);
        bc.setCategoryGap(20);
        bc.setMaxSize(520,291);
        //add all
        bc.getData().addAll(seriesA,seriesB,seriesC,seriesD,seriesF,seriesQ);
        this.graphPane.getChildren().add(bc);
    }
    /* Deletes all courses from currentUser object and selectedCourseListView... */
    private void deleteAllCourses(){
        //Access each item from list and delete it
        ObservableList<FutureCourse> futureCourseList = selectedCourseListView.getItems();
        while (!futureCourseList.isEmpty()){
            //Access the first course
            FutureCourse selectedFutureCourse = futureCourseList.get(0);
            User.currentUser().removeCourse(selectedFutureCourse);
            selectedCourseListView.getItems().remove(selectedFutureCourse);
            List<Agenda.AppointmentImplLocal> listToRemove = appointmentMap.get(appointmentGroupMap.get(selectedFutureCourse.toString()));
            scheduleView.appointments().removeAll(listToRemove);
            appointmentGroupMap.remove(selectedFutureCourse.toString());
            appointmentMap.remove(appointmentGroupMap.get(selectedFutureCourse.toString()));
            appointmentGroupMap.remove(selectedFutureCourse.toString());
            summaryTextView.setText(User.currentUser().toString());
            //Remove deleted course from list
            futureCourseList.remove(selectedFutureCourse);
        }
    }
    private void showAlert(Throwable e){
        System.out.println("In main thread: "+ e.getMessage());
        Stage errorStage = new Stage();
        errorStage.initOwner(this.rootStage);
        errorStage.initModality(Modality.WINDOW_MODAL);
        errorStage.setResizable(false);
        errorStage.setTitle("Error:(");
        String errorStr = e.getMessage();
        Label errorLabel = new Label(errorStr);
        errorStage.setScene(new Scene(new StackPane(errorLabel),300,100));
        errorStage.showAndWait();
    }
    private Stage getLoadingStage(){
        Stage loadingStage = new Stage();
        loadingStage.initOwner(this.rootStage);
        loadingStage.initModality(Modality.WINDOW_MODAL);
        loadingStage.setResizable(false);
        loadingStage.setTitle("Fetching Data...");
        this.indicator = new ProgressIndicator();
        this.indicator.setPrefSize(100,100);
        loadingStage.setScene(new Scene(new StackPane(this.indicator),200,160));
        this.indicator.setVisible(true);
        this.indicator.setProgress(-1);
        loadingStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                dataFetchingTask.cancel();
            }
        });
        return loadingStage;
    }
    private Stage getInfoStage(){
        Stage infoStage = new Stage();
        infoStage.initOwner(this.rootStage);
        infoStage.initModality(Modality.WINDOW_MODAL);
        infoStage.setResizable(false);
        infoStage.setTitle("About");
        String heart = "Made by Juliang\'18 with love,\n" +
                "Now updated by Jeffrey Cordero \'19!";
        Label aboutAuthor = new Label(heart);
        infoStage.setScene(new Scene(new StackPane(aboutAuthor),250,120));
        return infoStage;
    }
    /* Pop-up stage to warn that the load feature will erase the currently selected classes*/
    private Stage getLoadWarningStage(){
        Stage loadWarningStage = new Stage();
        loadWarningStage.initOwner(this.rootStage);
        loadWarningStage.initModality(Modality.WINDOW_MODAL);
        loadWarningStage.setResizable(false);
        loadWarningStage.setTitle("Load Warning");
        String warning = "Selecting a load file will\nerase any currently selected classes\n\n" +
                "Note: The load feature is not finished\nand classes may have changed (do check)";
        Label warningLabel = new Label(warning);
        warningLabel.setTextAlignment(TextAlignment.CENTER);
        loadWarningStage.setScene(new Scene(new StackPane(warningLabel),300,150));
        return loadWarningStage;
    }
}
