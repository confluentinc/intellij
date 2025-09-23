package io.confluent.kafka.core.ui.filter

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import io.confluent.kafka.core.settings.defaultui.UiUtil
import io.confluent.kafka.core.ui.MigPanel
import io.confluent.kafka.util.KafkaMessagesBundle
import com.michaelbaranov.microba.calendar.DatePicker
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JCheckBox
import javax.swing.JLabel

class DateFilterPopupComponent(@Nls label: String,
                               periodType: DatePeriodType,
                               begin: Date? = null,
                               end: Date? = null) : FilterPopupComponent(label) {

  companion object {
    //  DateFormatUtil.getDateTimeFormat().delegate
    var dateFormat = SimpleDateFormat("yyyy-MM-dd")
  }

  override var currentText = KafkaMessagesBundle.message("dateFilter.anyTime")

  private var periodType: DatePeriodType
  var from: Date? = null
  var to: Date? = null

  private val listeners = mutableListOf<DateFilterListener>()

  init {
    this.periodType = periodType
    when (periodType) {
      DatePeriodType.SPECIFIED -> setSelectedValues(begin, end)
      DatePeriodType.LAST_WEEK -> setLastWeekValues()
      DatePeriodType.LAST_DAY -> setLastDayValues()
    }
  }

  private fun updateCurrentText() {
    currentText = if (from != null && to != null) {
      dateFormat.format(from) + " - " + dateFormat.format(to)
    }
    else if (from == null && to == null) {
      KafkaMessagesBundle.message("dateFilter.any")
    }
    else if (from == null) {
      KafkaMessagesBundle.message("dateFilter.until") + " " + dateFormat.format(to)
    }
    else {
      KafkaMessagesBundle.message("dateFilter.from") + " " + dateFormat.format(from)
    }
  }

  fun addListener(listener: DateFilterListener) = listeners.add(listener)
  fun removeListener(listener: DateFilterListener) = listeners.remove(listener)

  private fun setSelectedValues(from: Date?, to: Date?) {
    periodType = DatePeriodType.SPECIFIED
    this.from = from
    this.to = to
    updateCurrentText()
    valueChanged()
  }

  private fun setLastWeekValues() {
    periodType = DatePeriodType.LAST_WEEK
    setDaysBefore(-7)
  }

  private fun setLastDayValues() {
    periodType = DatePeriodType.LAST_DAY
    setDaysBefore(-1)
  }

  private fun setDaysBefore(days: Int) {
    val cal = Calendar.getInstance()
    cal.time = Date()
    cal.add(Calendar.DAY_OF_YEAR, days)

    from = cal.time
    to = null

    updateCurrentText()
    valueChanged()
  }

  override fun createActionGroup(): ActionGroup {

    val allAction = DumbAwareAction.create(KafkaMessagesBundle.message("dateFilter.anyTime")) {
      setSelectedValues(null, null)
    }

    val lastDayAction = DumbAwareAction.create(KafkaMessagesBundle.message("dateFilter.lastDay")) {
      setLastDayValues()
    }

    val lastWeekAction = DumbAwareAction.create(KafkaMessagesBundle.message("dateFilter.lastWeek")) {
      setLastWeekValues()
    }

    val selectAction = object : DumbAwareAction(KafkaMessagesBundle.message("dateFilter.select")) {
      override fun actionPerformed(e: AnActionEvent) {
        val useDateFrom = JCheckBox(KafkaMessagesBundle.message("dateFilter.from"))
        val useDateTo = JCheckBox(KafkaMessagesBundle.message("dateFilter.to"))
        val dateFrom = DatePicker()
        val dateTo = DatePicker()

        val validationLabel = JLabel()
        validationLabel.isVisible = false
        validationLabel.foreground = JBColor.RED

        // Return true if dates range is valid.
        val validate : () -> Boolean =  {

          val from = if (useDateFrom.isSelected) dateFrom.date else null
          val to = if (useDateTo.isSelected) atEndOfDay(dateTo.date ) else null

          if (from != null && to != null && from >= to) {
            validationLabel.text = KafkaMessagesBundle.message("dateFilter.wrongRange")
            validationLabel.isVisible = true
          }
          else {
            validationLabel.isVisible = false
          }

          !validationLabel.isVisible
        }

        dateFrom.date = from ?: atStartOfDay(Date())
        dateTo.date = to ?: atEndOfDay(Date())
        useDateFrom.isSelected = from != null
        useDateTo.isSelected = to != null

        dateFrom.dateFormat = dateFormat
        dateTo.dateFormat = dateFormat

        dateFrom.addActionListener { validate() }
        dateTo.addActionListener { validate() }

        val listener = ActionListener {
          dateFrom.isEnabled = useDateFrom.isSelected
          dateTo.isEnabled = useDateTo.isSelected
          validate()
        }
        useDateFrom.addActionListener(listener)
        useDateTo.addActionListener(listener)

        // To update enabled state of date components.
        listener.actionPerformed(ActionEvent(this, 0, null))

        val panel = MigPanel().apply {
          add(useDateFrom);add(dateFrom, UiUtil.pushXGrowXSpanXWrap)
          add(useDateTo);add(dateTo, UiUtil.pushXGrowXSpanXWrap)
          add(validationLabel, UiUtil.pushXGrowXSpanXWrap)
        }

        val db = DialogBuilder(this@DateFilterPopupComponent)
        db.addCancelAction()
        db.addOkAction()
        db.setCenterPanel(panel)
        db.setPreferredFocusComponent(useDateFrom)
        db.setTitle(KafkaMessagesBundle.message("dateFilter.selectDialogHeader"))

        if (DialogWrapper.OK_EXIT_CODE == db.show()) {

          if(validate()) {
            // End date shifted to the end of the day.
            val endDate = if (useDateTo.isSelected) atEndOfDay(dateTo.date) else null
            setSelectedValues(if (useDateFrom.isSelected) dateFrom.date else null, endDate)
          }
        }
      }
    }

    return DefaultActionGroup(allAction,
                              selectAction,
                              lastDayAction,
                              lastWeekAction)
  }

  override fun valueChanged() {
    super.valueChanged()
    listeners.forEach { it.filterChanged(periodType, from, to) }
  }

  private fun atStartOfDay(date: Date): Date {

    val cal = Calendar.getInstance()
    cal.time = date

    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    return cal.time
  }

  private fun atEndOfDay(date: Date): Date {
    val cal = Calendar.getInstance()
    cal.time = date

    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)

    return cal.time
  }
}