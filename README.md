# Rapid-prototyping protection schemes with IEC 61850 #

The goal of this software is to automatically generate C/C++ code which reads and writes GOOSE and Sampled Value packets. Any valid IEC 61850 Substation Configuration Description (SCD) file, describing GOOSE and/or SV communications, can be used as the input. The output code is lightweight and platform-independent, so it can run on a variety of devices, including low-cost microcontrollers. It's ideal for rapid-prototyping new power system protection and control systems that require communications. But the software could also be used to implement the communications for any general purpose system.

This readme file describes how to set up the software, and its basic use.

*The code is meant to be a proof of concept, and is highly experimental. It has not been tested on many SCD files.*


## Installation ##

The software requires Eclipse, with the Eclipse Modeling Framework (EMF). Java Emitter Templates (JET) is also required. It's easiest to start with the version of Eclipse that comes with the Modeling Tools bundle (see here: http://www.eclipse.org/downloads/).

There are two source code trees: `emf` (in Java), and `c` (obviously written in C). Each should be a separate project in Eclipse. The Java `emf` project directory is organised as follows:

 - `src/`
   - `scdCodeGenerator/`: code that does the bulk of the conversion from an SCD file to C code. The class `Main` contains the `main()` function for the project, and contains the filename for the input SCD file.
   - `scdCodeGeneratorTemplates/`: template classes that are generated by JET.
   - `ch/`: the EMF Java model implementation. These files are all automatically generated by EMF, but are included in the repo for convenience.
 - `model/`: the IEC 61850 XML Schema files. EMF uses these to generate the model.
 - `templates/`: the template source files used by JET.

### EMF import process ###

Note that the SCL model has been augmented to help with code generation, so the original IEC 61850 XML Schema files are not used to generate the model. Instead, the augmented model is defined in `scl.ecore`.

 1. Create an "EMF Project" called "emf", at the location of the repository code.
 2. Select "Ecore model" as the Model Importer type.
 3. Select `scl.ecore` from the File System as the model URI.
 4. Select `scl` as the root package to import.
 5. Create a new project of type "Convert Projects to JET Projects", and select the `emf` project. For the `emf` project, go to Project Properties > JET Settings, and set Template Containers to "templates", and Source Container to "src". Delete the `sclToCHelper` directory in the root of `emf` that was created before JET was configured correctly.
 6. Open `SCL.genmodel` and right-click on the root of the model tree. Select "Show Properties View" and ensure that "Compliance Level" is set to "6.0". Right-click on the root again and select "Generate Model Code". This should re-generate the model implementation files, and set up the project properly for using the generated code.
 7. The package `org.eclipse.em.query` may need to be add to the Plug-in Dependencies. This can be done from the tooltip for the compiler error at the `import` statements.
 8. You may need to include an additional JAR library for the project to compile. In the Project Properties for `emf`, go to Java Build Path > Libraries. Click on "Add Extrenal JARs..." and find `com.ibm.icu_4.2.1.v20100412.jar` (or a similar version). It should be located in the "plugins" directory within the Eclipse installation.

<!--
### EMF import process ###

 1. Create an "EMF Project" called "emf", at the location of the repository code.
 2. Select "XML Schema" as the Model Importer type. Select all the IEC 61850 XML Schema documents in the `emf/model` directory.
 3. Select the three root packages that are imported (although, only `scl` is used).
 4. Create a new project of type "Convert Projects to JET Projects", and select the `emf` project. For the `emf` project, go to Project Properties > JET Settings, and set Template Containers to "templates", and Source Container to "src". Delete the `sclToCHelper` directory in the root of `emf` that was created before JET was configured correctly.
 5. Open `SCL.genmodel` and right-click on the root of the model tree. Select "Show Properties View" and ensure that "Compliance Level" is set to "6.0". Right-click on the root again and select "Generate Model Code". This should re-generate the model implementation files, and set up the project properly for using the generated code.
-->

### C code project example ###

An example SCD file and a `main.c` file are provided. Many of the other C files are generated automatically. For the C code to compile on Windows, you should have MinGW installed and add `C:\MinGW\bin;` to `PATH` in the Project Properties > C/C++ Build > Environment options. (Other compilers should work too.) In Project Properties > C/C++ Build > Settings > GCC Compiler Includes, set `"${workspace_loc:/${ProjName}/Include}"` as an include path. Also, in Project Properties > C/C++ Build > Settings > MinGW C Linker, add `wpcap` and `ws2_32` (assuming you are using Windows) to "Libraries" and add `"${workspace_loc:/${ProjName}/Lib}"` and `"C:\MinGW\lib"` to "Library search path". The WinPcap library files and header files (from http://www.winpcap.org/devel.htm) have been included in the repository for convenience; the PC must also have WinPcap driver installed (from http://www.winpcap.org/install/default.htm).

The accompanying mbed microcontroller example code is available [here](http://mbed.org/users/sblair/programs/rapid61850example/lyox9z). A [Processing](http://processing.org/) GUI is located in the `/processing/PACWorldClient` directory. For this to work, execute the example C project, start the microcontroller code, then start the Processing executable.


## Using the code ##

First open the file `Main.java`. In the `main()` function, set the argument of `generateCode(filename)` to the filename of the SCD file. The SCD file should be in the same directory as the `Main.java` file. Run the Java project to generate the C implementation.

A basic C `main()` function will look something like:

```C
#include "iec61850.h"

int length = 0;
unsigned char buffer[2048] = {0};

int main() {
	initialise_iec61850();											// initialise all data structures

	// send GOOSE packet
	PC_IED4.P1.CTRL.OUT_GGIO_1.SPCSO.stVal = TRUE;					// set a value that appears in the "GOOSE_outputs" Dataset
	length = gse_send_GOOSE_outputs_control_GT1(buffer, 1, 512);	// generate a goose packet, and store the bytes in "buffer"
	send_ethernet_packet(buffer, length);							// platform-specific call to send an Ethernet packet

	// receive GOOSE or SV packet
	length = recv_ethernet_packet(buffer);							// platform-specific call to receive an Ethernet packet
	gse_sv_packet_filter(buffer, length);							// deals with any GOOSE or SV dataset that is able to be processed
	boolean inputValue = IED_B.P1.CTRL.B_IN_GGIO_1.gse_inputs.PC_IED4_CTRL_OUT_stVal_1;		// read value that was updated by the packet

	return 0;
}
```

Clearly, a real implementation might include the use of platform-specific timers, interrupts and callbacks, where needed.

The generated code implements all IEDs specified in the SCD file. You can use the code to emulate the communications between several IEDs, or just use one IED's implementation.

### Callbacks after a dataset is decoded ###

Callbacks should be set up in the form:

```C
void SVcallbackFunction(CTYPE_INT16U smpCont) {
	;
}

D1Q1SB4.S1.C1.MMXU_1.sv_inputs.datasetDecodeDone = &SVcallbackFunction;
```

where `D1Q1SB4.S1.C1.MMXU_1` is a Logical Node defined in `datatypes.h` (and `ied.h`). After being initialised, this function will be called after this dataset is successfully decoded, to allow the LN to deal with the new data. For example, by default, only one packet of data is saved for each GSE or SV Control - and is overwritten when a new packet arrives. Therefore, it may be useful to use the callback to log the data to a seperate memory buffer.

### Platform-specific options ###

All platform-specific options can be edited in `ctypes.h` or `ctypes.c`. For example, for a big endian platform, change:

```C
#define LITTLE_ENDIAN		1
```

to:

```C
#define LITTLE_ENDIAN		0
```

All `CTYPE_*` definitions must map to local datatypes of the correct size and sign.

In `ctypes.c`, the basic library function `memcopy()` is used to copy bytes in order (according to platform endianness), and `reversememcpy()` copies the bytes of multi-byte data types in reverse order (for converting between endianness). Although these should work, they can be replaced with platform-specific alternatives for better performance.

## Known issues and possible features ##

 - Inputs - find ExtRef DA satisfied by container DO within a dataset, where the DA is not explicitly in a dataset
 - initialise default values (including DOI and DAI), and allocate memory for strings
    - typical values are set in DataTypeTemplates section (i.e., DA and BDA, both sub-types of AbstractDataType, may have Val elements)
    - special case values are set in LN definition
 - ensure C string literals are "safe", i.e. `\\` instead of `\`
 - put svControl and gseControl instances inside LLN0 definition?
