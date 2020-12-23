package com.tonicsystems.jarjar;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import java.io.File;

/**
 * @author Jonathan Gamba
 *         Date: 9/10/13
 */
public class InspectorTask extends Task {

    private String libraryPath;

    public void setLibraryPath ( String libraryPath ) {
        this.libraryPath = libraryPath;
    }

    @Override
    public void execute () throws BuildException {

        log( "Executing Packager task....", Project.MSG_INFO );

        // Validate input
        if ( this.libraryPath == null ) {
            throw new IllegalArgumentException( "No path element to inspect!" );
        }

        File libFolder = new File( this.libraryPath );
        log( "Reading from: " + libFolder.getAbsolutePath(), Project.MSG_INFO );

        //Create a formatter to read and display the results
        Formatter formatter = new PlainFormatter();
        formatter.setOutput( System.out );
        formatter.setDuplicatesOnly( true );

        //Create the inspector in order to analyze the given path
        Inspector inspector = new Inspector();
        inspector.addFormatter( formatter );
        inspector.inspect( libFolder );
        inspector.report( "Duplicated classes" );//Generate a report

        log( "Found " + inspector.getClassCount() + " unique classes", Project.MSG_INFO );
        log( "Found " + inspector.getDuplicateCount() + " duplicated classes", Project.MSG_INFO );
    }

}