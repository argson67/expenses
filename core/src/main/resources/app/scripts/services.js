'use strict';

angular.module('moneyApp')
  .factory('userSrv', function($http, $q, errSrv) {
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

    service.populateActiveUsers = function(data) {
      console.log("active users" + data.length);
      for (var i = 0; i < data.length; i++) {
        service.activeUsers.push(data[i]);
        console.log("Active user: " + data[i].name);
      }
    };

    service.getActiveUsers = function () {
      $http.post('api/activeUsers')
      .then(function (response) {
        service.resetActiveUsers();
        service.populateActiveUsers(response.data);
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

    service.getActiveUsers();

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
  .factory('redirectSrv', function($location) {
    var service = {};
    service.redirectUrl = null;

    service.redirect = function() {
      if (service.redirectUrl) {
        $location.path(service.redirectUrl);
        service.redirectUrl = null;
      }
    }

    service.setup = function(url) {
      service.redirectUrl = url;
    };

    return service;
  })
  .factory('errSrv', function($rootScope) {
    var service = {};

    service.errors = [];

    service.emit = function(err) {
      service.errors.push(err);
    }

    service.reset = function() {
      while(service.errors.length > 0) {
          service.errors.pop();
      }
    }

    return service;
  })
  .factory('expenseSrv', function($http, errSrv, userSrv, redirectSrv) {
    var service = {};

    service.activeUsers = userSrv.activeUsers;

    service.state = { addOrEdit: "Add", errLoading: false };

    service.expense = { }

    service.defaultExpense = {
      id: null,
      owner: userSrv.user,
      date: null,
      amount: 0.0,
      description: "description",
      comment: "comment",
      recurring: false,
      frequencyNum: 1,
      frequencyUnit: 0,
      beneficiaries: service.activeUsers.map(function (au) { return $.extend({ selected: true }, au); }),
      selected: false
    };

    service.reset = function (rec) {
      $.extend(service.expense, service.defaultExpense);
      service.expense.recurring = rec;
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
        } else {
          service.state.errLoading = false;
          $.extend(service.expense, response.data);
          service.expense.beneficiaries = service.activeUsers.map(function (au) {
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
      var data = {
        id: ex.id,
        ownerId: ex.owner.id,
        date: ex.date,
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
            if (userSrv.user.admin || userSrv.user.id == expense.owner.id) {
              $http.delete('api/expense/' + expense.id).then(function (response) {
                service.expenses.forEach(function (value, index) {
                  if (value.id == expense.id) {
                    service.expenses.splice(index, 1);
                  }
                });
              }, function (err) {
                errSrv.emit(err.data);
              });
            } else {
              errSrv.emit("You do not have the permissions to delete this expense.");
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

    service.mkModal = function(msg, action) {
      service.msg = msg;
      service.submit = function () {
        service.hide();
        console.log("Modal: performing action")
        action();
      }
      service.show();
    };

    return service;
  }).factory('reportSrv', function ($http) {
    var service = {};

    service.report = {};

    return service;
  }).factory('reportsSrv', function ($http) {
    var service = {};

    service.reports = [];

    return service;
  }).factory('debtsSrv', function ($http) {
    var service = {};

    service.myDebts = [];
    service.otherDebts = [];

    return service;
  });