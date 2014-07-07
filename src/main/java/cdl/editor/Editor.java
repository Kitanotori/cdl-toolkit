/**
 * @author Petri Kivikangas
 * @date 6.2.2012
 *
 */
package cdl.editor;

import java.awt.EventQueue;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.neo4j.cypher.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cdl.objects.Arc;
import cdl.objects.Concept;
import cdl.objects.Statement;
import cdl.objects.UW;
import cdl.query.CDLQuery;
import cdl.unl.Attributes;
import cdl.wrappers.EmbeddedBatchInserter;
import cdl.wrappers.NeoWrapper;

public class Editor {
	/* for CDL concept IDs */
	private static short number = 0;
	private static char character = 'A';

	JFrame frame;
	Logger logger;
	JTextField textfUW;
	DefaultComboBoxModel<UW> comboFormsModel;
	JComboBox<UW> comboForms;
	private JPanel panelStatus;
	JLabel lblStatus;
	private JTextArea textaResult;
	private JTextArea textaCypher;
	private JTextArea textaCDL;
	protected DefaultListModel<Concept> listConceptsModel;
	private JList<Arc> listRelations;
	private DefaultListModel<Arc> listRelationsModel;
	private JList<String> listAttributes;
	private JList<Concept> listConcepts;
	private JComboBox<Integer> comboExpansion;

	private static String getCode() {
		String code = "" + character + number++;
		if (number > 9) {
			number = 0;
			character++;
		}
		return code;
	}

	/**
	 * Launch the application.
	 */
	public static void main(final String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					Editor window = new Editor();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public Editor() {
		initialize();
		loadConfig();
		NeoWrapper.toggleRestWrapper();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {

		logger = LoggerFactory.getLogger(Editor.class);
		frame = new JFrame();
		frame.setBounds(100, 100, 819, 589);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("CDL Toolkit");

		comboFormsModel = new DefaultComboBoxModel<UW>();
		comboForms = new JComboBox<UW>(comboFormsModel);
		comboForms.setEditable(true);
		comboForms.setMaximumRowCount(20);
		JLabel lblForm = new JLabel("Form");
		lblForm.setLabelFor(comboForms);
		JScrollPane scrollCDL = new JScrollPane();
		JScrollPane scrollCypher = new JScrollPane();
		JScrollPane scrollAttributes = new JScrollPane();
		JScrollPane scrollRelations = new JScrollPane();
		JScrollPane scrollConcept = new JScrollPane();
		JLabel lblUw = new JLabel("UW");

		textfUW = new JTextField();
		textfUW.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					UW[] uws = NeoWrapper.fetchUWs(textfUW.getText());
					logger.info("Fetched "+uws.length+" UWs");
					comboFormsModel.removeAllElements();
					for (int i = 0; i < uws.length; i++) {
						comboFormsModel.addElement(uws[i]);
					}
				}
			}
		});
		lblUw.setLabelFor(textfUW);
		textfUW.setColumns(10);

		JButton btnAddPlain = new JButton("Add plain");
		btnAddPlain.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addConcept(new UW(textfUW.getText()));
			}
		});

		JButton btnAddUw = new JButton("Add UW");
		btnAddUw.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addConcept((UW) comboForms.getSelectedItem());
			}
		});

		JScrollPane scrollResult = new JScrollPane();

		JButton btnCdlCypher = new JButton("CDL -> Cypher");
		btnCdlCypher.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				generateCypherQuery();
			}
		});

		JButton btnRunCypher = new JButton("Run Cypher");
		btnRunCypher.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				runCypher();
			}
		});

		JButton btnAddAttributes = new JButton("Add attributes");
		btnAddAttributes.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addAttributes();
			}
		});

		JButton btnAddRelations = new JButton("Add relations");
		btnAddRelations.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				launchRelationMaker();
			}
		});

		JMenuItem mntmFileToDB = new JMenuItem("Import CDL files");
		mntmFileToDB.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				launchFileReader();
			}
		});

		panelStatus = new JPanel();

		JButton btnGenerateCDL = new JButton("Generate CDL");
		btnGenerateCDL.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				generateCDLQuery();
			}
		});

		comboExpansion = new JComboBox<Integer>();
		comboExpansion.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				generateCypherQuery();
			}

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
			}
		});
		comboExpansion.setModel(new DefaultComboBoxModel<Integer>(
				new Integer[] { 0, 1 }));
		comboExpansion.setSelectedIndex(0);

		JLabel lblExpansion = new JLabel("Expansion");

		GroupLayout groupLayout = new GroupLayout(frame.getContentPane());
		groupLayout
				.setHorizontalGroup(groupLayout
						.createParallelGroup(Alignment.LEADING)
						.addGroup(
								groupLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												groupLayout
														.createParallelGroup(
																Alignment.LEADING)
														.addComponent(
																scrollResult,
																GroupLayout.DEFAULT_SIZE,
																424,
																Short.MAX_VALUE)
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addComponent(
																				btnAddAttributes)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addComponent(
																				btnAddRelations)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addComponent(
																				btnGenerateCDL,
																				GroupLayout.DEFAULT_SIZE,
																				146,
																				Short.MAX_VALUE))
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addGroup(
																				groupLayout
																						.createParallelGroup(
																								Alignment.LEADING)
																						.addGroup(
																								groupLayout
																										.createSequentialGroup()
																										.addGroup(
																												groupLayout
																														.createParallelGroup(
																																Alignment.LEADING)
																														.addComponent(
																																lblForm)
																														.addComponent(
																																lblUw))
																										.addPreferredGap(
																												ComponentPlacement.RELATED)
																										.addGroup(
																												groupLayout
																														.createParallelGroup(
																																Alignment.LEADING)
																														.addComponent(
																																textfUW,
																																GroupLayout.DEFAULT_SIZE,
																																271,
																																Short.MAX_VALUE)
																														.addComponent(
																																comboForms,
																																0,
																																271,
																																Short.MAX_VALUE)))
																						.addGroup(
																								groupLayout
																										.createSequentialGroup()
																										.addComponent(
																												scrollAttributes,
																												GroupLayout.PREFERRED_SIZE,
																												95,
																												GroupLayout.PREFERRED_SIZE)
																										.addPreferredGap(
																												ComponentPlacement.RELATED)
																										.addComponent(
																												scrollConcept,
																												GroupLayout.DEFAULT_SIZE,
																												217,
																												Short.MAX_VALUE)))
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addGroup(
																				groupLayout
																						.createParallelGroup(
																								Alignment.LEADING,
																								false)
																						.addComponent(
																								scrollRelations,
																								0,
																								0,
																								Short.MAX_VALUE)
																						.addComponent(
																								btnAddUw,
																								GroupLayout.DEFAULT_SIZE,
																								GroupLayout.DEFAULT_SIZE,
																								Short.MAX_VALUE)
																						.addComponent(
																								btnAddPlain,
																								GroupLayout.DEFAULT_SIZE,
																								GroupLayout.DEFAULT_SIZE,
																								Short.MAX_VALUE))))
										.addPreferredGap(
												ComponentPlacement.RELATED)
										.addGroup(
												groupLayout
														.createParallelGroup(
																Alignment.LEADING)
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addComponent(
																				btnCdlCypher)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addComponent(
																				btnRunCypher))
														.addComponent(
																scrollCypher,
																Alignment.TRAILING,
																GroupLayout.DEFAULT_SIZE,
																359,
																Short.MAX_VALUE)
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addGap(12)
																		.addComponent(
																				lblExpansion)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addComponent(
																				comboExpansion,
																				GroupLayout.PREFERRED_SIZE,
																				56,
																				GroupLayout.PREFERRED_SIZE))
														.addComponent(
																scrollCDL,
																Alignment.TRAILING,
																GroupLayout.DEFAULT_SIZE,
																359,
																Short.MAX_VALUE))
										.addContainerGap())
						.addComponent(panelStatus, GroupLayout.DEFAULT_SIZE,
								813, Short.MAX_VALUE));
		groupLayout
				.setVerticalGroup(groupLayout
						.createParallelGroup(Alignment.TRAILING)
						.addGroup(
								groupLayout
										.createSequentialGroup()
										.addGap(12)
										.addGroup(
												groupLayout
														.createParallelGroup(
																Alignment.LEADING)
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addGap(12)
																		.addGroup(
																				groupLayout
																						.createParallelGroup(
																								Alignment.BASELINE)
																						.addComponent(
																								btnAddPlain)
																						.addGroup(
																								groupLayout
																										.createSequentialGroup()
																										.addGroup(
																												groupLayout
																														.createParallelGroup(
																																Alignment.LEADING)
																														.addComponent(
																																lblUw)
																														.addComponent(
																																textfUW,
																																GroupLayout.PREFERRED_SIZE,
																																27,
																																GroupLayout.PREFERRED_SIZE))
																										.addGap(57)
																										.addGroup(
																												groupLayout
																														.createParallelGroup(
																																Alignment.TRAILING)
																														.addGroup(
																																groupLayout
																																		.createParallelGroup(
																																				Alignment.BASELINE)
																																		.addComponent(
																																				scrollAttributes,
																																				GroupLayout.PREFERRED_SIZE,
																																				147,
																																				GroupLayout.PREFERRED_SIZE)
																																		.addComponent(
																																				scrollRelations,
																																				GroupLayout.PREFERRED_SIZE,
																																				147,
																																				GroupLayout.PREFERRED_SIZE))
																														.addComponent(
																																scrollConcept,
																																GroupLayout.PREFERRED_SIZE,
																																149,
																																GroupLayout.PREFERRED_SIZE)))
																						.addGroup(
																								groupLayout
																										.createSequentialGroup()
																										.addComponent(
																												scrollCDL,
																												GroupLayout.PREFERRED_SIZE,
																												202,
																												GroupLayout.PREFERRED_SIZE)
																										.addPreferredGap(
																												ComponentPlacement.RELATED,
																												7,
																												Short.MAX_VALUE)
																										.addGroup(
																												groupLayout
																														.createParallelGroup(
																																Alignment.BASELINE)
																														.addComponent(
																																lblExpansion)
																														.addComponent(
																																comboExpansion,
																																GroupLayout.PREFERRED_SIZE,
																																GroupLayout.DEFAULT_SIZE,
																																GroupLayout.PREFERRED_SIZE)))))
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addGap(58)
																		.addGroup(
																				groupLayout
																						.createParallelGroup(
																								Alignment.BASELINE)
																						.addComponent(
																								lblForm,
																								GroupLayout.PREFERRED_SIZE,
																								24,
																								GroupLayout.PREFERRED_SIZE)
																						.addComponent(
																								comboForms,
																								GroupLayout.PREFERRED_SIZE,
																								25,
																								GroupLayout.PREFERRED_SIZE)
																						.addComponent(
																								btnAddUw))))
										.addPreferredGap(
												ComponentPlacement.RELATED)
										.addGroup(
												groupLayout
														.createParallelGroup(
																Alignment.LEADING)
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addComponent(
																				scrollCypher,
																				GroupLayout.PREFERRED_SIZE,
																				200,
																				GroupLayout.PREFERRED_SIZE)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addGroup(
																				groupLayout
																						.createParallelGroup(
																								Alignment.BASELINE)
																						.addComponent(
																								btnCdlCypher)
																						.addComponent(
																								btnRunCypher)))
														.addGroup(
																groupLayout
																		.createSequentialGroup()
																		.addGroup(
																				groupLayout
																						.createParallelGroup(
																								Alignment.BASELINE)
																						.addComponent(
																								btnAddAttributes)
																						.addComponent(
																								btnAddRelations)
																						.addComponent(
																								btnGenerateCDL))
																		.addGap(18)
																		.addComponent(
																				scrollResult,
																				GroupLayout.PREFERRED_SIZE,
																				189,
																				GroupLayout.PREFERRED_SIZE)))
										.addPreferredGap(
												ComponentPlacement.RELATED, 25,
												Short.MAX_VALUE)
										.addComponent(panelStatus,
												GroupLayout.PREFERRED_SIZE, 20,
												GroupLayout.PREFERRED_SIZE)));

		lblStatus = new JLabel("");
		panelStatus.add(lblStatus);

		JLabel lblConcepts = new JLabel("Concepts");
		scrollConcept.setColumnHeaderView(lblConcepts);

		listConceptsModel = new DefaultListModel<Concept>();
		listConcepts = new JList<Concept>(listConceptsModel);
		listConcepts.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					deleteConcepts();
				}
			}
		});
		scrollConcept.setViewportView(listConcepts);

		JLabel lblQueryResult = new JLabel("Query result");
		scrollResult.setColumnHeaderView(lblQueryResult);

		textaResult = new JTextArea();
		scrollResult.setViewportView(textaResult);

		JLabel lblRelations = new JLabel("Relations");
		scrollRelations.setColumnHeaderView(lblRelations);

		listRelationsModel = new DefaultListModel<Arc>();
		listRelations = new JList<Arc>(listRelationsModel);
		listRelations.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				deleteRelations();
			}
		});
		scrollRelations.setViewportView(listRelations);

		JLabel lblAttributes = new JLabel("Attributes");
		scrollAttributes.setColumnHeaderView(lblAttributes);

		listAttributes = new JList(getAttributes());
		scrollAttributes.setViewportView(listAttributes);

		JLabel lblCypher = new JLabel("Cypher query");
		scrollCypher.setColumnHeaderView(lblCypher);

		textaCypher = new JTextArea();
		scrollCypher.setViewportView(textaCypher);

		JLabel lblCDL = new JLabel("CDL Query");
		scrollCDL.setColumnHeaderView(lblCDL);

		textaCDL = new JTextArea();
		scrollCDL.setViewportView(textaCDL);
		frame.getContentPane().setLayout(groupLayout);

		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);

		JMenu mnActions = new JMenu("Actions");
		menuBar.add(mnActions);

		JMenuItem mntmToggleEmbedded = new JMenuItem("Toggle embedded");
		mntmToggleEmbedded.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				NeoWrapper.toggleEmbeddedWrapper();
			}
		});
		mnActions.add(mntmToggleEmbedded);

		JMenuItem mntmStopDB = new JMenuItem("Toggle REST API");
		mntmStopDB.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				NeoWrapper.toggleRestWrapper();
			}
		});
		mnActions.add(mntmStopDB);

		JMenuItem mntmTestConnection = new JMenuItem("Test connection");
		mntmTestConnection.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				try {
					if (NeoWrapper.isConnected()) {
						lblStatus.setText("Connection to "
								+ NeoWrapper.impl().accessPoint() + " works");
					} else {
						lblStatus.setText("Problem with connection");
					}
				} catch (Exception ex) {
					lblStatus.setText("Problem with connection: "
							+ ex.getMessage());
				}
			}
		});

		JMenuItem mntmToggleBatch = new JMenuItem("Toggle batch insert");
		mntmToggleBatch.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				NeoWrapper.toggleEmbeddedBatchWrapper();
			}
		});
		mnActions.add(mntmToggleBatch);

		JMenuItem mntmStopBatch = new JMenuItem("Flush batch");
		mntmStopBatch.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				EmbeddedBatchInserter ebi = (EmbeddedBatchInserter) NeoWrapper
						.impl();
				ebi.flush();
			}
		});
		mnActions.add(mntmStopBatch);
		mnActions.add(mntmTestConnection);

		JMenuItem mntmImportOntology = new JMenuItem("Import UNL Ontology");
		mntmImportOntology.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				NeoWrapper.importOntology();
			}
		});
		mnActions.add(mntmImportOntology);

		mnActions.add(mntmFileToDB);

		JMenuItem mntmCleanDB = new JMenuItem("Clean database");
		mntmCleanDB.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				NeoWrapper.cleanDB();
			}
		});
		mnActions.add(mntmCleanDB);

		JMenuItem mntmReadConf = new JMenuItem("Reload configurations");
		mntmReadConf.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				loadConfig();
			}
		});
		mnActions.add(mntmReadConf);
	}

	String[] getAttributes() {
		return Attributes.getAttributes();
	}

	void loadConfig() {
		Path confFile = FileSystems.getDefault().getPath(
				"resources/application.conf"); // Path to the config
												// file
		Config.readConfig(confFile.toFile());
	}

	void generateCDLQuery() {
		Enumeration<Concept> c = listConceptsModel.elements();
		ArrayList<Concept> c2 = new ArrayList<Concept>();
		while (c.hasMoreElements())
			c2.add(c.nextElement());
		Enumeration<Arc> a = listRelationsModel.elements();
		ArrayList<Arc> a2 = new ArrayList<Arc>();
		while (a.hasMoreElements())
			a2.add(a.nextElement());
		Statement s = new Statement(c2, a2);
		textaCDL.setText(s.toString());
	}

	void addRelation(Arc rel) {
		listRelationsModel.addElement(rel);
	}

	void addConcept(UW uw) {
		Concept c = new Concept(getCode(), uw.hw(), uw.constraints());
		listConceptsModel.addElement(c);
	}

	/**
	 * Add selected attributes to the selected concept
	 */
	void addAttributes() {
		List<Concept> c = listConcepts.getSelectedValuesList();
		List<String> attrs = listAttributes.getSelectedValuesList();
		Iterator<Concept> i = c.iterator();
		while (i.hasNext()) {
			i.next().addAttrs(attrs);
		}
	}

	void generateCypherQuery() {
		String cypher = "";
		if (!textaCDL.getText().trim().isEmpty() && NeoWrapper.isConnected()) {
			cypher = CDLQuery.getCypher(textaCDL.getText(),
					comboExpansion.getSelectedIndex());
		}
		textaCypher.setText(cypher);
	}

	void deleteConcepts() {
		int[] selected = listConcepts.getSelectedIndices();
		for (int i = 0; i < selected.length; i++) {
			listConceptsModel.remove(i);
		}
	}

	void deleteRelations() {
		int[] selected = listRelations.getSelectedIndices();
		for (int i = 0; i < selected.length; i++) {
			listRelationsModel.remove(i);
		}
	}

	void launchRelationMaker() {
		RelationMaker rm = new RelationMaker(this);
		rm.setVisible(true);
	}

	void launchFileReader() {
		FileReader fr = new FileReader();
		fr.setVisible(true);
	}

	void runCypher() {
		if (NeoWrapper.isConnected()) {
			ExecutionResult result = NeoWrapper.query(textaCypher.getText());
			if (!result.javaIterator().hasNext()) {
				logger.info("Returned empty result");
				textaResult.setText("");
			} else {
				logger.info("Query found a match");
				textaResult.setText(result.dumpToString());
			}
		} else {
			logger.info("Tried to run query without connection to DB");
			lblStatus.setText("Cannot run query without connection to DB");
		}
	}
}
