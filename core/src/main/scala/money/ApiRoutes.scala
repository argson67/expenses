package money

trait ApiRoutes extends Admin with Auth with UserRoutes with ExpenseRoutes {
  self: MoneyService =>

  def apiRoute = authRoute ~ pathPrefix("admin")(adminRoute) ~ userRoute ~ expenseRoute
}
