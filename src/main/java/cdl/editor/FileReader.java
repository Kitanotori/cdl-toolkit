/**
 * @author Petri Kivikangas
 * @date 6.2.2012
 *
 */
package cdl.editor;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cdl.objects.CDLDocument;
import cdl.parser.CDLParser;
import cdl.wrappers.NeoWrapper;

public class FileReader extends JFrame {
	private static final long serialVersionUID = -4229835275010876845L;

	private JPanel fileReader;
	private JFileChooser fileChooser;
	private JTextArea textaParsed;
	private Logger logger;

	/**
	 * Create the frame.
	 */
	public FileReader() {
		logger = LoggerFactory.getLogger(Editor.class);
		setTitle("File reader");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setBounds(100, 100, 1048, 543);
		fileReader = new JPanel();
		fileReader.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(fileReader);

		JScrollPane scrollPane = new JScrollPane();

		JButton btnParseFile = new JButton("Parse file");

		fileChooser = new JFileChooser(new File(System.getProperty("user.dir")));
		fileChooser.setMultiSelectionEnabled(true);

		btnParseFile.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				parseFile();
			}
		});

		JButton btnAddToDb = new JButton("Add to DB");
		btnAddToDb.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addToDb();
			}
		});

		GroupLayout gl_fileReader = new GroupLayout(fileReader);
		gl_fileReader
				.setHorizontalGroup(gl_fileReader
						.createParallelGroup(Alignment.LEADING)
						.addGroup(
								gl_fileReader
										.createSequentialGroup()
										.addComponent(fileChooser,
												GroupLayout.PREFERRED_SIZE,
												509, GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												ComponentPlacement.RELATED)
										.addGroup(
												gl_fileReader
														.createParallelGroup(
																Alignment.LEADING)
														.addGroup(
																gl_fileReader
																		.createSequentialGroup()
																		.addComponent(
																				btnParseFile)
																		.addGap(63)
																		.addComponent(
																				btnAddToDb)
																		.addContainerGap(
																				250,
																				Short.MAX_VALUE))
														.addComponent(
																scrollPane,
																GroupLayout.DEFAULT_SIZE,
																517,
																Short.MAX_VALUE))));
		gl_fileReader
				.setVerticalGroup(gl_fileReader
						.createParallelGroup(Alignment.TRAILING)
						.addGroup(
								gl_fileReader
										.createSequentialGroup()
										.addGroup(
												gl_fileReader
														.createParallelGroup(
																Alignment.TRAILING)
														.addComponent(
																fileChooser,
																GroupLayout.DEFAULT_SIZE,
																493,
																Short.MAX_VALUE)
														.addGroup(
																gl_fileReader
																		.createSequentialGroup()
																		.addContainerGap()
																		.addComponent(
																				scrollPane,
																				GroupLayout.DEFAULT_SIZE,
																				450,
																				Short.MAX_VALUE)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addGroup(
																				gl_fileReader
																						.createParallelGroup(
																								Alignment.BASELINE)
																						.addComponent(
																								btnParseFile)
																						.addComponent(
																								btnAddToDb))))
										.addContainerGap()));

		textaParsed = new JTextArea();
		scrollPane.setViewportView(textaParsed);
		fileReader.setLayout(gl_fileReader);
	}

	void parseFile() {
		logger.debug("Parsing selected files..");
		File[] files = fileChooser.getSelectedFiles();
		CDLDocument doc;
		for (int i = 0; i < files.length; i++) {
			doc = (new CDLParser(files[i], files[i].getName())).parseDocument();
			textaParsed.setText(doc.toString());
		}
	}

	void addToDb() {
		File[] files = fileChooser.getSelectedFiles();
		NeoWrapper.importDocuments(files);
	}
}
