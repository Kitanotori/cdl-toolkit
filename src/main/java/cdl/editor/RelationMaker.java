/**
 * @author Petri Kivikangas
 * @date 6.2.2012
 *
 */
package cdl.editor;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.EmptyBorder;

import cdl.neo4j.wrappers.RelTypes;
import cdl.objects.Arc;
import cdl.objects.Concept;

public class RelationMaker extends JFrame {
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private Editor parent;
	private JList<Concept> listFrom;
	private JList<Concept> listTo;
	private JComboBox<String> comboRelation;

	/**
	 * Create the frame.
	 */
	public RelationMaker(final Editor par) {
		setTitle("Relation maker");
		this.parent = par;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setBounds(100, 100, 450, 229);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);

		JScrollPane scrollFrom = new JScrollPane();

		JScrollPane scrollTo = new JScrollPane();

		comboRelation = new JComboBox<String>(RelTypes.getTypes());

		JLabel lblRelation = new JLabel("Relation");

		JButton btnAdd = new JButton("Add");
		btnAdd.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addRelation();
			}
		});

		GroupLayout gl_contentPane = new GroupLayout(contentPane);
		gl_contentPane
				.setHorizontalGroup(gl_contentPane
						.createParallelGroup(Alignment.LEADING)
						.addGroup(
								gl_contentPane
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(scrollFrom,
												GroupLayout.PREFERRED_SIZE,
												147, GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												ComponentPlacement.RELATED)
										.addGroup(
												gl_contentPane
														.createParallelGroup(
																Alignment.LEADING)
														.addComponent(
																comboRelation,
																GroupLayout.PREFERRED_SIZE,
																92,
																GroupLayout.PREFERRED_SIZE)
														.addComponent(
																lblRelation)
														.addGroup(
																gl_contentPane
																		.createSequentialGroup()
																		.addGap(12)
																		.addComponent(
																				btnAdd)))
										.addPreferredGap(
												ComponentPlacement.UNRELATED)
										.addComponent(scrollTo,
												GroupLayout.PREFERRED_SIZE,
												127, GroupLayout.PREFERRED_SIZE)
										.addContainerGap(32, Short.MAX_VALUE)));
		gl_contentPane
				.setVerticalGroup(gl_contentPane
						.createParallelGroup(Alignment.LEADING)
						.addGroup(
								gl_contentPane
										.createSequentialGroup()
										.addGroup(
												gl_contentPane
														.createParallelGroup(
																Alignment.LEADING)
														.addGroup(
																gl_contentPane
																		.createSequentialGroup()
																		.addContainerGap()
																		.addGroup(
																				gl_contentPane
																						.createParallelGroup(
																								Alignment.BASELINE)
																						.addComponent(
																								scrollFrom,
																								GroupLayout.PREFERRED_SIZE,
																								155,
																								GroupLayout.PREFERRED_SIZE)
																						.addComponent(
																								scrollTo,
																								GroupLayout.PREFERRED_SIZE,
																								155,
																								GroupLayout.PREFERRED_SIZE)))
														.addGroup(
																gl_contentPane
																		.createSequentialGroup()
																		.addGap(50)
																		.addComponent(
																				lblRelation)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addComponent(
																				comboRelation,
																				GroupLayout.PREFERRED_SIZE,
																				25,
																				GroupLayout.PREFERRED_SIZE)
																		.addPreferredGap(
																				ComponentPlacement.RELATED)
																		.addComponent(
																				btnAdd)))
										.addContainerGap(95, Short.MAX_VALUE)));

		JLabel lblTo = new JLabel("To concept");
		scrollTo.setColumnHeaderView(lblTo);

		JLabel lblFrom = new JLabel("From concept");
		scrollFrom.setColumnHeaderView(lblFrom);

		int size = par.listConceptsModel.getSize();
		Concept[] cons = new Concept[size];
		for (int i = 0; i < size; i++) {
			cons[i] = par.listConceptsModel.get(i);
		}
		listFrom = new JList<Concept>(cons);
		scrollFrom.setViewportView(listFrom);

		listTo = new JList<Concept>(cons);
		scrollTo.setViewportView(listTo);
		contentPane.setLayout(gl_contentPane);
	}

	void addRelation() {
		String from = listFrom.getSelectedValue().rlabel();
		String rel = (String) comboRelation.getSelectedItem();
		String to = listTo.getSelectedValue().rlabel();
		Arc relation = new Arc(from, rel, to);
		parent.addRelation(relation);
	}
}
