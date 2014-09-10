package money

trait ApiRoutes extends Admin with Auth with UserRoutes with ExpenseRoutes with ReportRoutes with DebtRoutes {
  self: MoneyService =>

  def apiRoute = authRoute ~ pathPrefix("admin")(adminRoute) ~ userRoute ~ expenseRoute ~ reportRoute ~ debtRoute
}
