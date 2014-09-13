'use strict';

String.prototype.capitalize = function() {
  return this.charAt(0).toUpperCase() + this.slice(1);
}

function mkSort($scope, init, prefix) {
  function withPrefix(str) {
    if (prefix) {
      return prefix + str.capitalize();
    } else {
      return str;
    }
  };

  var nme = {
    sortBy: withPrefix("sortBy"),
    sortReverse: withPrefix("sortReverse"),
    sortOnClick: withPrefix("sortOnClick"),
    caret: withPrefix("caret")
  };

  console.log("mkSort: " + JSON.stringify(nme));

  $scope[nme.sortBy] = init;
  $scope[nme.sortReverse] = false;

  $scope[nme.sortOnClick] = function (field) {
    if ($scope[nme.sortBy] == field) {
      $scope[nme.sortReverse] = !$scope[nme.sortReverse];
    } else {
      $scope[nme.sortReverse] = false;
    }

    $scope[nme.sortBy] = field;
  };

  $scope[nme.caret] = function (field) {
    //console.log("caret: " + field)

    if ($scope[nme.sortBy] == field) {
      if ($scope[nme.sortReverse]) {
        return "fa fa-caret-up fa-fw"
      } else {
        return "fa fa-caret-down fa-fw"
      }
    } else {
      return "fa fa-fw"
    }
  };
};

angular.module('moneyApp')
  .controller('MainCtrl', function ($scope) {
    $scope.awesomeThings = [
      'HTML5 Boilerplate',
      'AngularJS'
    ];
  })
  .controller('ErrorCtrl', function($scope, $location, errSrv) {
    $scope.awesomeThings = [
      'HTML5 Boilerplate',
      'AngularJS'
    ];
    $scope.errors = errSrv.errors;

    $scope.$on('$locationChangeStart', function(event) {
      errSrv.reset();
    });
  })
  .controller('MenuCtrl', function($scope, userSrv, authSrv) {
    $scope.awesomeThings = [
      'HTML5 Boilerplate',
      'AngularJS'
    ];

    $scope.user = userSrv.user;

    $scope.printUser = function () {
      return JSON.stringify($scope.user);
    };

    $scope.logout = function() {
      return authSrv.logout();
    }
  })
  .controller('AuthCtrl', function ($scope, authSrv) {
    $scope.awesomeThings = [
      'HTML5 Boilerplate',
      'AngularJS'
    ];

    $scope.login = function() {
      return authSrv.login($scope.username, $scope.password)
    }

    $scope.register = function() {
      return authSrv.register($scope.username, $scope.name, $scope.email, $scope.password)
    }
  })
  .controller('ExpensesViewCtrl', function ($scope, $location, userSrv, expensesSrv, redirectSrv) {
    $scope.awesomeThings = [
      'HTML5 Boilerplate',
      'AngularJS'
    ];

    $scope.fromDate = null;
    $scope.toDate = null;

    $scope.user = userSrv.user;

    $scope.rowClass = function (committed) {
      if (committed) {
        return "info";
      } else {
        return "";
      }
    };

    $scope.expenses = expensesSrv.expenses;
    $scope.deleteSelected = expensesSrv.deleteSelected;
    $scope.selectAll = function () {
      $scope.expenses.forEach(function (expense) {
        if (!expense.committed && ($scope.user.admin || $scope.user.id == expense.owner.id)) {
          expense.selected = true;
        }
      });
    };
    $scope.deselectAll = function () {
      $scope.expenses.forEach(function (expense) {
        expense.selected = false;
      });
    };
    $scope.editExpense = function (expenseId) {
      redirectSrv.setup($location.path());
      $location.path('/expenses/edit/' + expenseId);
    };

    $scope.thisMonth = function () {
      var today = new Date();
      $scope.fromDate = new Date(today.getFullYear(), today.getMonth(), 1, 0, 0, 0, 0);
      $scope.toDate = new Date(today.getFullYear(), today.getMonth() + 1, 0, 0, 0, 0, 0);
    };

    $scope.lastMonth = function () {
      var today = new Date();
      $scope.fromDate = new Date(today.getFullYear(), today.getMonth() - 1, 1, 0, 0, 0, 0);
      $scope.toDate = new Date(today.getFullYear(), today.getMonth(), 0, 0, 0, 0, 0);
    };

    $scope.$on('$routeChangeSuccess', function () {
      console.log("Getting expenses from " + $scope.fromDate + " to " + $scope.toDate);
      expensesSrv.getExpenses($scope.fromDate, $scope.toDate);
    });

    $scope.$watch('[fromDate, toDate]', function (newValue, oldValue) {
      expensesSrv.getExpenses($scope.fromDate, $scope.toDate);
    }, true);

    mkSort($scope, "date");
  })
  .controller('ExpensesViewRecCtrl', function($scope, $location, userSrv, expensesSrv, redirectSrv) {
    $scope.user = userSrv.user;

    $scope.expenses = expensesSrv.expenses;
    $scope.deleteSelected = expensesSrv.deleteSelected;
    $scope.editExpense = function (expenseId) {
      redirectSrv.setup($location.path());
      $location.path('/expenses/edit/' + expenseId);
    };

    $scope.selectAll = function () {
      $scope.expenses.forEach(function (expense) {
        if (!expense.committed && ($scope.user.admin || $scope.user.id == expense.owner.id)) {
          expense.selected = true;
        }
      });
    };
    $scope.deselectAll = function () {
      $scope.expenses.forEach(function (expense) {
        expense.selected = false;
      });
    };

    $scope.$on('$routeChangeSuccess', function () {
      console.log("Getting recurring expenses");
      expensesSrv.getRecurringExpenses();
    });

    mkSort($scope, "date");
  })
  .controller('ExpensesEditCtrl', function ($scope, $routeParams, userSrv, redirectSrv, expenseSrv) {
    $scope.state = expenseSrv.state;
    $scope.expense = expenseSrv.expense;
    $scope.recurring = function () {
      if ($scope.expense.recurring) {
        return "recurring ";
      } else {
        return "";
      }
    };

    $scope.submit = expenseSrv.submit;

    $scope.$on('$routeChangeSuccess', function () {
      console.log("Getting active users");
      userSrv.getActiveUsers(function () {
        if ($routeParams.id) {
          $scope.expense.owner = null; // Hack to hide the form until the expense has been loaded
          redirectSrv.orElse("/expenses/view");
          expenseSrv.loadExpense($routeParams.id);
        } else {
          console.log("Resetting expense");
          if ($routeParams.rec == "true") {
            redirectSrv.orElse("/expenses/viewRec");
          } else {
            redirectSrv.orElse("/expenses/view");
          }
          expenseSrv.reset($routeParams.rec == "true");
        }

        //$('#amount').maskMoney('mask');
      });
    });

    //$('#amount').maskMoney({thousands: '', decimal: '.', allowZero: false, allowNegative: false, prefix: '$'});
  }).controller('ModalCtrl', function($scope, modalSrv) {
    $scope.modal = modalSrv;
  })
  .controller('ReportsListCtrl', function ($scope, $location, reportsSrv) {
    $scope.reports = reportsSrv.reports;

    $scope.viewReport = function (id) {
      $location.path("/reports/view/" + id);
    };

    $scope.$on('$routeChangeSuccess', function () {
      reportsSrv.getReports();
    });

    mkSort($scope, "date");
  })
  .controller('ReportsGenCtrl', function ($scope, reportsSrv) {
    $scope.fromDate = null;
    $scope.toDate = null;

    $scope.thisMonth = function () {
      var today = new Date();
      $scope.fromDate = new Date(today.getFullYear(), today.getMonth(), 1, 0, 0, 0, 0);
      $scope.toDate = new Date(today.getFullYear(), today.getMonth() + 1, 0, 0, 0, 0, 0);
    };

    $scope.lastMonth = function () {
      var today = new Date();
      $scope.fromDate = new Date(today.getFullYear(), today.getMonth() - 1, 1, 0, 0, 0, 0);
      $scope.toDate = new Date(today.getFullYear(), today.getMonth(), 0, 0, 0, 0, 0);
    };

    $scope.submit = function () {
      reportsSrv.generate($scope.fromDate, $scope.toDate);
    };
  })
  .controller('ReportsViewCtrl', function ($scope, $routeParams, userSrv, reportSrv) {
    $scope.report = reportSrv.report;
    $scope.debts = reportSrv.debts;
    $scope.expenses = reportSrv.expenses;

    $scope.payDebt = reportSrv.payDebt;
    $scope.confirmDebt = reportSrv.confirmDebt;

    $scope.$on('$routeChangeSuccess', function () {
      if ($routeParams.id) {
        reportSrv.getReport($routeParams.id);
      }
    });

    $scope.rowClass = function (debt) {
      if (debt.confirmed && (debt.debtor.id == userSrv.user.id || debt.creditor.id == userSrv.user.id)) {
        return "info";
      } else if (debt.paid && (debt.debtor.id == userSrv.user.id || debt.creditor.id == userSrv.user.id)) {
        return "warning";
      } else if (debt.debtor.id == userSrv.user.id) {
        return "danger";
      } else if (debt.creditor.id == userSrv.user.id) {
        return "success";
      } else {
        return "";
      }
    };

    mkSort($scope, "date", "ex");
    mkSort($scope, "creditor.name", "db");
  })
  .controller('DebtsCtrl', function ($scope, userSrv, debtsSrv) {
    $scope.myDebts = debtsSrv.myDebts;
    $scope.otherDebts = debtsSrv.otherDebts;

    $scope.payDebt = debtsSrv.payDebt;
    $scope.confirmDebt = debtsSrv.confirmDebt;

    $scope.rowClass = function (debt) {
      if (debt.confirmed && (debt.debtor.id == userSrv.user.id || debt.creditor.id == userSrv.user.id)) {
        return "info";
      } else if (debt.paid && (debt.debtor.id == userSrv.user.id || debt.creditor.id == userSrv.user.id)) {
        return "warning";
      } else if (debt.debtor.id == userSrv.user.id) {
        return "danger";
      } else if (debt.creditor.id == userSrv.user.id) {
        return "success";
      } else {
        return "";
      }
    };

    $scope.$on('$routeChangeSuccess', function () {
      debtsSrv.getDebts();
    });

    mkSort($scope, "creditor.name", "my");
    mkSort($scope, "debtor.name", "other");
  });
