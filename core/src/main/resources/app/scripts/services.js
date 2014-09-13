'use strict';

Array.prototype.findIndex = function(predicate) {
  if (this == null) {
    throw new TypeError('Array.prototype.find called on null or undefined');
  }
  if (typeof predicate !== 'function') {
    throw new TypeError('predicate must be a function');
  }
  var list = Object(this);
  var length = list.length >>> 0;
  var thisArg = arguments[1];
  var value;

  for (var i = 0; i < length; i++) {
    value = list[i];
    if (predicate.call(thisArg, value, i, list)) {
      return i;
    }
  }
  return -1;
};

function mkDebt(service, $http, userSrv, errSrv) {
  service.payDebt = function (debt) {
    if (debt.paid) {
      errSrv.emit("Debt has already been marked as paid.");
    } else if (debt.debtor.id != userSrv.user.id) {
      errSrv.emit("You cannot repay someone else's debt.");
    } else {
      $http.post("api/debts/pay/" + debt.id)
      .then(function (response) {
        debt.paid = true;
      }, function (err) {
        errSrv.emit(err.data);
      });
    }
  };

  service.confirmDebt = function (debt) {
    if (!debt.paid) {
      errSrv.emit("Cannot confirm an unpaid debt.");
    } else if (debt.confirmed) {
      errSrv.emit("Debt has already been confirmed.");
    } else if (debt.creditor.id != userSrv.user.id) {
      errSrv.emit("You cannot confirm a debt not owed to you.");
    } else {
      $http.post("api/debts/confirm/" + debt.id)
      .then(function (response) {
        debt.confirmed = true;
        if (debt.lst) {
          var i = debt.lst.findIndex(function (d) { return d.id == debt.id });
          debt.lst.splice(i, 1);
        }
      }, function (err) {
        errSrv.emit(err.data);
      });
    }
  };
};

angular.module('moneyApp')
  .factory('userSrv', function($http, $q, $rootScope, errSrv) {
    var service = { user: {}, activeUsers: [] };

    var defaultUser = {
      id: null,
      name: null,
      email: null,
      login: null,
      admin: null,
      active: null
    };

    service.populateUserData = function(data) {
      $.extend(service.user, data)
    };

    service.resetActiveUsers = function () {
      while(service.activeUsers.length > 0) {
        service.activeUsers.pop();
      }
    };

    service.populateActiveUsers = function (data) {
      console.log("active users" + data.length);
      for (var i = 0; i < data.length; i++) {
        service.activeUsers.push(data[i]);
        console.log("Active user: " + data[i].name);
      }
    };

    service.getActiveUsers = function (callback) {
      $http.post('api/activeUsers')
      .then(function (response) {
        service.resetActiveUsers();
        service.populateActiveUsers(response.data);
        callback();
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    service.getUser = function (id) {
      var deferred = $q.defer();

      $http.get('api/user/' + id)
      .then(function (response) {
        deferred.resolve(response.data);
      }, function (err) {
        deferred.reject("Error loading user '" + id + "': " + err.data);
      });

      return deferred.promise;
    };

    service.reset = function() {
      $.extend(service.user, defaultUser);
    };

    service.checkActiveLogin = function (success, failure) {
      $http.get('api/whoami')
      .then(function (response) {
        service.populateUserData(response.data);
        success && success();
      }, function (err) {
        console.log("No login detected: " + err.data);
        service.reset();
        failure && failure();
      });
    }

    return service;
  })
  .factory('authSrv', function ($location, $http, userSrv, errSrv) {
    var service = {};

    service.login = function (username, password) {
      $http.post('api/login', {
        username: username,
        password: password
      })
      .then(function (response) {
        userSrv.populateUserData(response.data)
        $location.path('/');
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    service.logout = function () {
      $http.post('api/logout')
      .then(function (response) {
        userSrv.reset();
        $location.path('/');
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    service.register = function (username, name, email, password) {
      $http.post('api/register', {
        username: username,
        name    : name,
        email   : email,
        password: password
      })
      .then(function (response) {
        $location.path('/login');
      }, function (err) {
        errSrv.emit(err.data);
      });
    }

    return service;
  })
  .factory('redirectSrv', function ($location) {
    var service = {};
    service.redirectUrl = null;

    service.redirect = function () {
      if (service.redirectUrl) {
        $location.path(service.redirectUrl);
        service.redirectUrl = null;
      }
    }

    service.setup = function (url) {
      service.redirectUrl = url;
    };

    service.orElse = function (url) {
      if (!service.redirectUrl) {
        service.redirectUrl = url;
      }
    };

    return service;
  })
  .factory('errSrv', function ($rootScope) {
    var service = {};

    service.errors = [];

    service.emit = function (err) {
      service.errors.push(err);
    }

    service.reset = function () {
      while(service.errors.length > 0) {
          service.errors.pop();
      }
    }

    return service;
  })
  .factory('expenseSrv', function ($http, $filter, errSrv, userSrv, redirectSrv) {
    var service = {};

    service.state = { addOrEdit: "Add", errLoading: false };

    service.expense = { }

    service.defaultExpense = function (rec) {
      var date = null;

      if (!rec) {
        date = new Date();
      }

      var res = {
        id: null,
        owner: userSrv.user,
        date: date,
        amount: 0.01,
        description: "description",
        comment: "comment",
        recurring: rec,
        frequencyNum: 1,
        frequencyUnit: 0,
        beneficiaries: userSrv.activeUsers.map(function (au) { return $.extend({ selected: true }, au); }),
        selected: false
      };

      console.log("HOORAH: " + JSON.stringify(userSrv.activeUsers));

      return res;
    };

    service.reset = function (rec) {
      $.extend(service.expense, service.defaultExpense(rec));
      service.state.errLoading = false;
      service.state.addOrEdit = "Add";
    };

    service.validate = function () {
      var isValid = true;

      errSrv.reset();

      if (isNaN(service.expense.amount)) {
        errSrv.emit("'Amount' must be a number");
        isValid = false;
      }
      if (service.expense.description == "") {
        errSrv.emit("'Reason' is a required field");
        isValid = false;
      }
      if (service.expense.beneficiaries.findIndex(function (ben) { return ben.selected; }) == -1) {
        errSrv.emit("You must select at least one beneficiary");
        isValid = false;
      }

      return isValid;
    };

    service.loadExpense = function (id) {
      var uri = 'api/expense/' + id;
      console.log("Loading id '" + id + "' from uri: "+ uri);
      $http.get(uri)
      .then(function (response) {
        if (!userSrv.user.admin && response.data.owner.id != userSrv.user.id) {
          service.reset(false);
          service.state.errLoading = true;
          errSrv.emit("You do not have permissions to edit this expense.");
        } else if (response.data.committed) {
          service.reset(false);
          service.state.errLoading = true;
          errSrv.emit("You cannot edit a committed expense.");
        } else {
          service.state.errLoading = false;
          $.extend(service.expense, response.data);
          if (service.expense.date) {
            //
            var ms = Date.parse(service.expense.date);

            service.expense.date = new Date(ms + new Date().getTimezoneOffset() * 60000);
          }
          // TODO: Fix the comma thing properly
          service.expense.amount = $filter('currency')(service.expense.amount, "").replace(",", "");
          service.expense.beneficiaries = userSrv.activeUsers.map(function (au) {
            var isBen = service.expense.beneficiaries.filter(function (ben) { return au.id == ben.id; }).length == 1;
            return $.extend({ selected: isBen }, au);
          });
        }
      }, function (err) {
        service.reset(false);
        service.state.errLoading = true;
        errSrv.emit(err.data);
      })
      .finally(function () {
        service.state.addOrEdit = "Edit";
      });
    };

    service.submit = function () {
      if (!service.validate()) {
        return;
      }

      var ex = service.expense;
      var newDate = null;
      if (ex.date) {
        newDate = $filter('date')(ex.date, "yyyy-MM-dd");
      }
      var data = {
        id: ex.id,
        ownerId: ex.owner.id,
        date: newDate,
        amount: parseFloat(ex.amount).toFixed(2),
        description: ex.description,
        comment: ex.comment,
        recurring: ex.recurring,
        frequencyNum: ex.frequencyNum,
        frequencyUnit: ex.frequencyUnit,
        beneficiaries: ex.beneficiaries.filter(function (ben) {
          return ben.selected;
        }).map(function (ben) {
          return ben.id;
        }),
        selected: false
      };

      var req = null;
      if (ex.id) {
        req = $http.post('api/expense/' + ex.id, data);
      } else {
        req = $http.put('api/expense', data);
      };

      req.then(function (response) {
        redirectSrv.redirect();
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    service.reset(false);

    return service;
  })
  .factory('expensesSrv', function($http, $q, $filter, errSrv, userSrv, redirectSrv, modalSrv) {
    var service = { expenses: [] };

    var unit2Str = {
      0: "days",
      1: "weeks",
      2: "months"
    }

    service.populateRecurringExpenses = function (data) {
      data.forEach(function (expense) {
        expense.frequency = expense.frequencyNum + " " + unit2Str[expense.frequencyUnit];
        expense.beneficiaries = expense.beneficiaries.map(function (user) { return user.name; }).sort().join(", ");
        service.expenses.push(expense);
      });
    };

    service.populateExpenses = function (data) {
      data.forEach(function (expense) {
        expense.beneficiaries = expense.beneficiaries.map(function (user) { return user.name; }).sort().join(", ");
        service.expenses.push(expense);
      });
    }

    service.getRecurringExpenses = function () {
      $http.get('api/recurringExpenses')
      .then(function (response) {
        service.resetExpenses();
        service.populateRecurringExpenses(response.data);
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    service.getExpenses = function (fromDateRaw, toDateRaw) {
      service.resetExpenses();
      if (fromDateRaw && toDateRaw) {
        var fromDate = $filter('date')(fromDateRaw, "yyyy-MM-dd");
        var toDate = $filter('date')(toDateRaw, "yyyy-MM-dd");

        $http.get('api/expenses/' + fromDate + '/' + toDate)
        .then(function (response) {
          service.populateExpenses(response.data);
        }, function (err) {
          errSrv.emit(err.data);
        });
      }
    };

    service.resetExpenses = function () {
      while(service.expenses.length > 0) {
        service.expenses.pop();
      }
    };

    service.deleteSelected = function () {
      var selectedExpenses = service.expenses.filter(function (expense) { return expense.selected; });
      if (selectedExpenses.length > 0) {
        var msg = "Are you sure you want to delete the selected expenses?";
        var onSuccess = function () {
          selectedExpenses.forEach(function (expense) {
            if (!userSrv.user.admin && userSrv.user.id != expense.owner.id) {
              errSrv.emit("You do not have the permissions to delete this expense.");
            } else if (expense.committed) {
              errSrv.emit("You do not delete a committed expense.");
            } else {
              $http.delete('api/expense/' + expense.id).then(function (response) {
                var i = service.expenses.findIndex(function (e) { return e.id == expense.id });
                service.expenses.splice(i, 1);
              }, function (err) {
                errSrv.emit(err.data);
              });
            }
          });
        }

        modalSrv.mkModal(msg, onSuccess);
      }
    }

    service.sort = function (arr, field, reverse) {
      var dynamicSort = function (a, b) {
        var sortOrder = reverse ? -1 : 1;
        var result = (a[field] < b[field]) ? -1 : (a[field] > b[field]) ? 1 : 0;
        return result * sortOrder;
      };

      arr.sort(dynamicSort);
    };

    return service;
  }).factory('modalSrv', function () {
    var service = {};
    service.show = function () {
      $("#bsModal").modal('show');
    };
    service.hide = function () {
      $("#bsModal").modal('hide');
    };
    service.msg = "";
    service.cancel = function () { service.hide(); }
    service.submit = function () { service.hide(); }

    service.mkModal = function (msg, action) {
      service.msg = msg;
      service.submit = function () {
        service.hide();
        console.log("Modal: performing action")
        action();
      }
      service.show();
    };

    return service;
  }).factory('reportSrv', function ($http, userSrv, errSrv) {
    var service = {};

    var emptyReport = {
      id: null,
      date: null,
      description: null
    };

    service.report = {};
    service.expenses = [];
    service.debts = [];

    service.reset = function () {
      while(service.expenses.length > 0) {
        service.expenses.pop();
      }

      while(service.debts.length > 0) {
        service.debts.pop();
      }

      $.extend(service.report, emptyReport);
    };

    service.populateReport = function (data) {
      $.extend(service.report, data);
    };

    service.populateExpenses = function (data) {
      // TODO: Dedup
      data.forEach(function (expense) {
        expense.beneficiaries = expense.beneficiaries.map(function (user) { return user.name; }).sort().join(", ");
        service.expenses.push(expense);
      });
    };

    service.populateDebts = function (data) {
      data.forEach(function (debt) {
        service.debts.push(debt);
      });
    };

    service.getReport = function (id) {
      service.reset();

      $http.get('api/report/' + id)
      .then(function (response) {
        service.populateReport(response.data);
      }, function (err) {
        errSrv.emit(err.data);
      });

      $http.get('api/expense/report/' + id)
      .then(function (response) {
        service.populateExpenses(response.data);
      }, function (err) {
        errSrv.emit(err.data);
      });

      $http.get('api/debts/report/' + id)
      .then(function (response) {
        service.populateDebts(response.data);
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    mkDebt(service, $http, userSrv, errSrv);

    return service;
  }).factory('reportsSrv', function ($http, $location, $filter, errSrv, modalSrv) {
    var service = {};

    service.reports = [];

    service.reset = function () {
      while(service.reports.length > 0) {
        service.reports.pop();
      }
    };

    service.populateReports = function (data) {
      data.forEach(function (report) {
        service.reports.push(report);
      });
    };

    service.getReports = function () {
      service.reset();

      $http.get('api/reports')
      .then(function (response) {
        service.populateReports(response.data);
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    service.generate = function (fromDateRaw, toDateRaw) {
      if (fromDateRaw && toDateRaw) {
        var fromDate = $filter('date')(fromDateRaw, "yyyy-MM-dd");
        var toDate = $filter('date')(toDateRaw, "yyyy-MM-dd");
        var msg = "Are you sure you want to commit all expenses from " + fromDate + " to " + toDate + "? This operation cannot be undone.";
        var onSuccess = function () {
          $http.post("api/report/generate/" + fromDate + "/" + toDate)
          .then(function (response) {
            // TODO: redirect to the newly-created report?
            $location.path("/reports/list");
          }, function (err) {
            errSrv.emit(err.data);
          });
        };

        modalSrv.mkModal(msg, onSuccess);
      }
    };

    return service;
  }).factory('debtsSrv', function ($http, userSrv, errSrv) {
    var service = {};

    service.myDebts = [];
    service.otherDebts = [];

    service.reset = function () {
      while(service.myDebts.length > 0) {
        service.myDebts.pop();
      }
      while(service.otherDebts.length > 0) {
        service.otherDebts.pop();
      }
    };

    service.populateMyDebts = function (data) {
      data.forEach(function (debt) {
        debt.lst = service.myDebts;
        service.myDebts.push(debt);
      });
    };

    service.populateOtherDebts = function (data) {
      data.forEach(function (debt) {
        debt.lst = service.otherDebts;
        service.otherDebts.push(debt);
      });
    };

    service.getDebts = function () {
      service.reset();

      $http.get('api/debts/debtor/' + userSrv.user.id)
      .then(function (response) {
        service.populateMyDebts(response.data);
      }, function (err) {
        errSrv.emit(err.data);
      });

      $http.get('api/debts/creditor/' + userSrv.user.id)
      .then(function (response) {
        service.populateOtherDebts(response.data);
      }, function (err) {
        errSrv.emit(err.data);
      });
    };

    mkDebt(service, $http, userSrv, errSrv);

    return service;
  });