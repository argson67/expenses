'use strict';

String.prototype.capitalize = function() {
  return this.charAt(0).toUpperCase() + this.slice(1);
}

function mkSort($scope, init = null, prefix = null) {
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

    $scope.expenses = expensesSrv.expenses;
    $scope.deleteSelected = expensesSrv.deleteSelected;
    $scope.selectAll = function () {
      $scope.expenses.forEach(function (expense) {
        if ($scope.user.admin || $scope.user.id == expense.owner.id) {
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
    $scope.deleteExpense = expensesSrv.deleteExpense;
    $scope.editExpense = function (expenseId) {
      redirectSrv.setup($location.path());
      $location.path('/expenses/edit/' + expenseId);
    }

    $scope.$on('$routeChangeSuccess', function () {
      console.log("Getting recurring expenses");
      expensesSrv.getRecurringExpenses();
    });

    mkSort($scope, "date");
  })
  .controller('ExpensesEditCtrl', function ($scope, $routeParams, userSrv, expenseSrv) {
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
      userSrv.getActiveUsers();
    });

    if ($routeParams.id) {
      $scope.expense.owner = null; // Hack to hide the form until the expense has been loaded
      expenseSrv.loadExpense($routeParams.id);
    } else {
      expenseSrv.reset($routeParams.rec == "true");
    }
  }).controller('ModalCtrl', function($scope, modalSrv) {
    $scope.modal = modalSrv;
  })
  .controller('ReportsListCtrl', function ($scope, reportsSrv) {
    $scope.reports = reportsSrv.reports;

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
  })
  .controller('ReportsViewCtrl', function ($scope, reportSrv) {
    $scope.report = reportSrv.report;
  })
  .controller('DebtsCtrl', function ($scope, debtsSrv) {
    $scope.myDebts = debtsSrv.myDebts;
    $scope.otherDebts = debtsSrv.otherDebts;

    mkSort($scope, "creditor.name", "my");
    mkSort($scope, "debtor.name", "other");
  });
